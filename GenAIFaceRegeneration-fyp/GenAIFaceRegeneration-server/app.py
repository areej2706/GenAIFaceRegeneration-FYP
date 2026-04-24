from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import torch
import torch.nn as nn
import torchvision.transforms as T
from PIL import Image, ImageEnhance, ImageFilter
import io
import base64
import os
from datetime import datetime, timedelta
from functools import wraps
import jwt
from werkzeug.security import generate_password_hash, check_password_hash
import uuid

# Import database configuration
from database import init_db, get_db, close_db, User, ImageHistory

app = Flask(__name__)
CORS(app)
app.config['SECRET_KEY'] = os.environ.get('SECRET_KEY', 'your-secret-key-change-in-production')

# Quality enhancement settings
QUALITY_PRESETS = {
    'low': {'resolution': 256, 'sharpen': 1.0, 'enhance': 1.0},
    'medium': {'resolution': 256, 'sharpen': 1.3, 'enhance': 1.1},
    'high': {'resolution': 256, 'sharpen': 1.5, 'enhance': 1.2},
    'ultra': {'resolution': 256, 'sharpen': 1.8, 'enhance': 1.3}
}

# Default attributes (fixed - attributes have no effect on inference due to InstanceNorm)
# NOTE: the pretrained model expects 4 attribute channels (attr_dim=4).
DEFAULT_ATTRIBUTES = [1, 0, 1, 0]

# --- MODEL ARCHITECTURE ---
class UNetGenerator(nn.Module):
    def __init__(self, attr_dim=4):
        super(UNetGenerator, self).__init__()
        
        def down_block(in_c, out_c, normalize=True, dropout=0.0):
            layers = [nn.Conv2d(in_c, out_c, 4, 2, 1, bias=False)]
            if normalize:
                layers.append(nn.InstanceNorm2d(out_c))
            layers.append(nn.LeakyReLU(0.2, inplace=True))
            if dropout > 0:
                layers.append(nn.Dropout(dropout))
            return nn.Sequential(*layers)
        
        def up_block(in_c, out_c, dropout=0.0):
            layers = [
                nn.ConvTranspose2d(in_c, out_c, 4, 2, 1, bias=False),
                nn.InstanceNorm2d(out_c),
                nn.ReLU(inplace=True)
            ]
            if dropout > 0:
                layers.append(nn.Dropout(dropout))
            return nn.Sequential(*layers)
        
        # Encoder (named to match checkpoint keys)
        self.d1 = down_block(3, 64, normalize=False)
        self.d2 = down_block(64, 128)
        self.d3 = down_block(128, 256)
        self.d4 = down_block(256, 512)
        self.d5 = down_block(512, 512)
        self.d6 = down_block(512, 512)
        self.d7 = down_block(512, 512)
        self.d8 = down_block(512, 512, normalize=False)
        
        # Decoder
        self.up1 = up_block(512 + attr_dim, 512, dropout=0.5)
        self.up2 = up_block(1024, 512, dropout=0.5)
        self.up3 = up_block(1024, 512, dropout=0.5)
        self.up4 = up_block(1024, 512)
        self.up5 = up_block(1024, 256)
        self.up6 = up_block(512, 128)
        self.up7 = up_block(256, 64)
        self.final = nn.Sequential(
            nn.Upsample(scale_factor=2, mode='bilinear', align_corners=True),
            nn.InstanceNorm2d(128),
            nn.Conv2d(128, 3, 4, stride=1, padding=1, bias=True),
            nn.Tanh()
        )
    
    def forward(self, x, attr):
        d1 = self.d1(x); d2 = self.d2(d1); d3 = self.d3(d2); d4 = self.d4(d3)
        d5 = self.d5(d4); d6 = self.d6(d5); d7 = self.d7(d6); d8 = self.d8(d7)
        
        attr = attr.view(attr.size(0), attr.size(1), 1, 1)
        d8_cat = torch.cat([d8, attr], dim=1)
        
        u1 = torch.cat([self.up1(d8_cat), d7], dim=1)
        u2 = torch.cat([self.up2(u1), d6], dim=1)
        u3 = torch.cat([self.up3(u2), d5], dim=1)
        u4 = torch.cat([self.up4(u3), d4], dim=1)
        u5 = torch.cat([self.up5(u4), d3], dim=1)
        u6 = torch.cat([self.up6(u5), d2], dim=1)
        u7 = torch.cat([self.up7(u6), d1], dim=1)
        
        return self.final(u7)

# --- LOAD MODEL ---
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model = UNetGenerator().to(device)
model.load_state_dict(torch.load('model/pencil2pixel.pth', map_location=device))
model.eval()

transform = T.Compose([
    T.Resize((256, 256)),
    T.ToTensor(),
    T.Normalize((0.5, 0.5, 0.5), (0.5, 0.5, 0.5))
])

# --- IMAGE ENHANCEMENT FUNCTIONS ---
def enhance_output_image(img, quality='medium'):
    """
    Apply post-processing enhancements to improve output quality
    """
    preset = QUALITY_PRESETS.get(quality, QUALITY_PRESETS['medium'])
    
    # 1. Sharpen the image
    if preset['sharpen'] > 1.0:
        enhancer = ImageEnhance.Sharpness(img)
        img = enhancer.enhance(preset['sharpen'])
    
    # 2. Enhance contrast
    if preset['enhance'] > 1.0:
        enhancer = ImageEnhance.Contrast(img)
        img = enhancer.enhance(preset['enhance'])
    
    # 3. Slight color enhancement
    enhancer = ImageEnhance.Color(img)
    img = enhancer.enhance(1.1)
    
    # 4. Apply unsharp mask for better details
    img = img.filter(ImageFilter.UnsharpMask(radius=2, percent=150, threshold=3))
    
    return img

def preprocess_input_image(img):
    """
    Preprocess input image to reduce artifacts and improve quality
    """
    # Convert to RGB if needed
    if img.mode != 'RGB':
        img = img.convert('RGB')
    
    # Apply slight denoising
    img = img.filter(ImageFilter.MedianFilter(size=3))
    
    # Enhance contrast slightly
    enhancer = ImageEnhance.Contrast(img)
    img = enhancer.enhance(1.05)
    
    return img

# Initialize database on startup
init_db()

# --- AUTHENTICATION MIDDLEWARE ---
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = request.headers.get('Authorization')
        
        if not token:
            return jsonify({'error': 'Token is missing'}), 401
        
        try:
            if token.startswith('Bearer '):
                token = token[7:]
            data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
            current_user_id = data['user_id']
        except jwt.ExpiredSignatureError:
            return jsonify({'error': 'Token has expired'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'error': 'Invalid token'}), 401
        
        return f(current_user_id, *args, **kwargs)
    
    return decorated

# --- API ENDPOINTS ---

# --- AUTHENTICATION ENDPOINTS ---
@app.route('/auth/signup', methods=['POST'])
def signup():
    db = get_db()
    try:
        data = request.get_json()
        
        if not data or not data.get('username') or not data.get('email') or not data.get('password'):
            return jsonify({'error': 'Username, email, and password are required'}), 400
        
        username = data['username']
        email = data['email']
        password = data['password']
        
        if len(password) < 6:
            return jsonify({'error': 'Password must be at least 6 characters'}), 400
        
        # Check if user exists
        existing_user = db.query(User).filter(
            (User.username == username) | (User.email == email)
        ).first()
        
        if existing_user:
            return jsonify({'error': 'Username or email already exists'}), 409
        
        # Create new user
        user_id = str(uuid.uuid4())
        password_hash = generate_password_hash(password)
        
        new_user = User(
            user_id=user_id,
            username=username,
            email=email,
            password_hash=password_hash
        )
        
        db.add(new_user)
        db.commit()
        
        # Generate token
        token = jwt.encode({
            'user_id': user_id,
            'exp': datetime.utcnow() + timedelta(days=7)
        }, app.config['SECRET_KEY'], algorithm='HS256')
        
        return jsonify({
            'message': 'User created successfully',
            'user': {
                'user_id': user_id,
                'username': username,
                'email': email
            },
            'token': token
        }), 201
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/auth/login', methods=['POST'])
def login():
    db = get_db()
    try:
        data = request.get_json()
        
        if not data or not data.get('email') or not data.get('password'):
            return jsonify({'error': 'Email and password are required'}), 400
        
        email = data['email']
        password = data['password']
        
        user = db.query(User).filter(User.email == email).first()
        
        if not user or not check_password_hash(user.password_hash, password):
            return jsonify({'error': 'Invalid email or password'}), 401
        
        # Generate token
        token = jwt.encode({
            'user_id': user.user_id,
            'exp': datetime.utcnow() + timedelta(days=7)
        }, app.config['SECRET_KEY'], algorithm='HS256')
        
        return jsonify({
            'message': 'Login successful',
            'user': {
                'user_id': user.user_id,
                'username': user.username,
                'email': user.email
            },
            'token': token
        }), 200
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/auth/profile', methods=['GET'])
@token_required
def get_profile(current_user_id):
    db = get_db()
    try:
        user = db.query(User).filter(User.user_id == current_user_id).first()
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        image_count = db.query(ImageHistory).filter(ImageHistory.user_id == current_user_id).count()
        
        return jsonify({
            'user': {
                'user_id': user.user_id,
                'username': user.username,
                'email': user.email,
                'created_at': user.created_at.isoformat() if user.created_at else None,
                'total_images': image_count
            }
        }), 200
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/auth/profile', methods=['PUT'])
@token_required
def update_profile(current_user_id):
    db = get_db()
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        
        user = db.query(User).filter(User.user_id == current_user_id).first()
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        if 'username' in data:
            existing = db.query(User).filter(
                User.username == data['username'],
                User.user_id != current_user_id
            ).first()
            if existing:
                return jsonify({'error': 'Username already exists'}), 409
            user.username = data['username']
        
        if 'email' in data:
            existing = db.query(User).filter(
                User.email == data['email'],
                User.user_id != current_user_id
            ).first()
            if existing:
                return jsonify({'error': 'Email already exists'}), 409
            user.email = data['email']
        
        if 'password' in data:
            if len(data['password']) < 6:
                return jsonify({'error': 'Password must be at least 6 characters'}), 400
            user.password_hash = generate_password_hash(data['password'])
        
        user.updated_at = datetime.utcnow()
        db.commit()
        
        return jsonify({
            'message': 'Profile updated successfully',
            'user': {
                'user_id': user.user_id,
                'username': user.username,
                'email': user.email,
                'updated_at': user.updated_at.isoformat()
            }
        }), 200
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

# --- IMAGE HISTORY ENDPOINTS ---
@app.route('/history', methods=['GET'])
@token_required
def get_history(current_user_id):
    db = get_db()
    try:
        page = request.args.get('page', 1, type=int)
        limit = request.args.get('limit', 10, type=int)
        offset = (page - 1) * limit
        
        images = db.query(ImageHistory).filter(
            ImageHistory.user_id == current_user_id
        ).order_by(ImageHistory.created_at.desc()).limit(limit).offset(offset).all()
        
        total = db.query(ImageHistory).filter(ImageHistory.user_id == current_user_id).count()
        
        return jsonify({
            'images': [{
                'image_id': img.image_id,
                'original_filename': img.original_filename,
                'attributes': img.attributes,
                'created_at': img.created_at.isoformat() if img.created_at else None
            } for img in images],
            'pagination': {
                'page': page,
                'limit': limit,
                'total': total,
                'pages': (total + limit - 1) // limit
            }
        }), 200
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/history/<image_id>', methods=['GET'])
@token_required
def get_history_image(current_user_id, image_id):
    db = get_db()
    try:
        image = db.query(ImageHistory).filter(
            ImageHistory.image_id == image_id,
            ImageHistory.user_id == current_user_id
        ).first()
        
        if not image:
            return jsonify({'error': 'Image not found'}), 404
        
        return send_file(
            io.BytesIO(image.generated_image),
            mimetype='image/png',
            as_attachment=True,
            download_name=f"{image.original_filename or 'generated'}.png"
        )
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/history/<image_id>', methods=['DELETE'])
@token_required
def delete_history_image(current_user_id, image_id):
    db = get_db()
    try:
        image = db.query(ImageHistory).filter(
            ImageHistory.image_id == image_id,
            ImageHistory.user_id == current_user_id
        ).first()
        
        if not image:
            return jsonify({'error': 'Image not found'}), 404
        
        db.delete(image)
        db.commit()
        
        return jsonify({'message': 'Image deleted successfully'}), 200
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/history', methods=['DELETE'])
@token_required
def clear_history(current_user_id):
    db = get_db()
    try:
        deleted_count = db.query(ImageHistory).filter(
            ImageHistory.user_id == current_user_id
        ).delete()
        
        db.commit()
        
        return jsonify({
            'message': 'History cleared successfully',
            'deleted_count': deleted_count
        }), 200
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'device': device})

@app.route('/generate', methods=['POST'])
@token_required
def generate(current_user_id):
    db = get_db()
    try:
        # Check if image file is present
        if 'image' not in request.files:
            return jsonify({'error': 'No image file provided'}), 400
        
        file = request.files['image']
        if file.filename == '':
            return jsonify({'error': 'Empty filename'}), 400
        
        # Get quality setting
        quality = request.form.get('quality', 'medium')
        if quality not in QUALITY_PRESETS:
            quality = 'medium'
        
        # Get attributes (fixed defaults - attributes have no effect on model output)
        attributes = request.form.get('attributes', None)
        
        if attributes:
            attr_list = [int(x) for x in attributes.split(',')]
            if len(attr_list) != 4:
                return jsonify({'error': 'Attributes must be 4 values'}), 400
        else:
            attr_list = DEFAULT_ATTRIBUTES[:]
        
        # Process image with preprocessing
        img = Image.open(file.stream).convert('RGB')
        original_size = img.size
        
        # Preprocess input
        img = preprocess_input_image(img)
        
        sketch_tensor = transform(img).unsqueeze(0).to(device)
        attr_tensor = torch.tensor(attr_list).float().view(1, 4).to(device)
        
        # Generate
        with torch.no_grad():
            output = model(sketch_tensor, attr_tensor)
        
        # Denormalize and convert to PIL
        output = (output.squeeze().cpu() + 1) / 2
        output_img = T.ToPILImage()(output)
        
        # Apply post-processing enhancements
        output_img = enhance_output_image(output_img, quality)
        
        # Optionally resize to larger size for better quality
        upscale = request.form.get('upscale', 'false').lower() == 'true'
        if upscale:
            # Upscale to 2x using high-quality resampling
            new_size = (512, 512)
            output_img = output_img.resize(new_size, Image.Resampling.LANCZOS)
        
        # Save to history if requested
        save_to_history = request.form.get('save', 'false').lower() == 'true'
        image_id = None
        
        if save_to_history:
            img_io = io.BytesIO()
            output_img.save(img_io, 'PNG')
            img_bytes = img_io.getvalue()
            
            image_id = str(uuid.uuid4())
            attr_str = ','.join(map(str, attr_list))
            
            new_image = ImageHistory(
                image_id=image_id,
                user_id=current_user_id,
                original_filename=file.filename,
                generated_image=img_bytes,
                attributes=attr_str
            )
            
            db.add(new_image)
            db.commit()
        
        # Return format
        return_format = request.form.get('format', 'image')  # 'image' or 'base64'
        
        if return_format == 'base64':
            # Return as base64 JSON
            buffered = io.BytesIO()
            output_img.save(buffered, format="PNG")
            img_str = base64.b64encode(buffered.getvalue()).decode()
            
            response = {
                'image': img_str, 
                'format': 'base64',
                'quality': quality,
                'size': output_img.size
            }
            if image_id:
                response['image_id'] = image_id
                response['saved'] = True
            return jsonify(response)
        else:
            # Return as image file
            img_io = io.BytesIO()
            output_img.save(img_io, 'PNG')
            img_io.seek(0)
            return send_file(img_io, mimetype='image/png')
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

@app.route('/generate-batch', methods=['POST'])
@token_required
def generate_batch(current_user_id):
    db = get_db()
    try:
        if 'images' not in request.files:
            return jsonify({'error': 'No images provided'}), 400
        
        files = request.files.getlist('images')
        attributes = request.form.get('attributes', None)
        quality = request.form.get('quality', 'medium')
        upscale = request.form.get('upscale', 'false').lower() == 'true'
        
        if quality not in QUALITY_PRESETS:
            quality = 'medium'
        
        if attributes:
            attr_list = [int(x) for x in attributes.split(',')]
            if len(attr_list) != 4:
                return jsonify({'error': 'Attributes must be 4 values'}), 400
        else:
            attr_list = [1, 0, 1, 0]
        
        results = []
        attr_tensor = torch.tensor(attr_list).float().view(1, 4).to(device)
        attr_str = ','.join(map(str, attr_list))
        
        save_to_history = request.form.get('save', 'false').lower() == 'true'
        
        for file in files:
            img = Image.open(file.stream).convert('RGB')
            
            # Preprocess input
            img = preprocess_input_image(img)
            
            sketch_tensor = transform(img).unsqueeze(0).to(device)
            
            with torch.no_grad():
                output = model(sketch_tensor, attr_tensor)
            
            output = (output.squeeze().cpu() + 1) / 2
            output_img = T.ToPILImage()(output)
            
            # Apply post-processing enhancements
            output_img = enhance_output_image(output_img, quality)
            
            # Optionally upscale
            if upscale:
                new_size = (512, 512)
                output_img = output_img.resize(new_size, Image.Resampling.LANCZOS)
            
            buffered = io.BytesIO()
            output_img.save(buffered, format="PNG")
            img_bytes = buffered.getvalue()
            img_str = base64.b64encode(img_bytes).decode()
            
            result = {
                'filename': file.filename, 
                'image': img_str,
                'quality': quality,
                'size': output_img.size
            }
            
            if save_to_history:
                image_id = str(uuid.uuid4())
                
                new_image = ImageHistory(
                    image_id=image_id,
                    user_id=current_user_id,
                    original_filename=file.filename,
                    generated_image=img_bytes,
                    attributes=attr_str
                )
                
                db.add(new_image)
                result['image_id'] = image_id
                result['saved'] = True
            
            results.append(result)
        
        if save_to_history:
            db.commit()
        
        return jsonify({'results': results, 'count': len(results)})
    
    except Exception as e:
        db.rollback()
        return jsonify({'error': str(e)}), 500
    finally:
        close_db(db)

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
