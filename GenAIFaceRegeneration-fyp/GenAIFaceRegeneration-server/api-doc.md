# Pencil2Pixel API Documentation

## Overview
The Pencil2Pixel API converts pencil sketches into realistic images using a UNet-based deep learning model. The API is built with Flask and supports both single and batch image processing.

**Base URL:** `http://localhost:5000`

**Model:** SketchToImageGenerator with 4-channel input (RGB + Mask) and 4 tone attributes
**Resolution:** 512x512 (can upscale to 1024x1024)
**Device:** CUDA (if available) or CPU

---

## Authentication

Most endpoints require authentication using JWT (JSON Web Token). After signup or login, include the token in the `Authorization` header:

```
Authorization: Bearer <your-token>
```

**Token Expiration:** 7 days

---

## Endpoints

### Authentication Endpoints

#### 1. Signup

**Endpoint:** `POST /auth/signup`

**Description:** Create a new user account.

**Request:**
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "securepassword123"
}
```

**Response:**
```json
{
  "message": "User created successfully",
  "user": {
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error Responses:**
```json
{"error": "Username, email, and password are required"}
{"error": "Password must be at least 6 characters"}
{"error": "Username or email already exists"}
```

**Status Codes:**
- `201 Created` - User created successfully
- `400 Bad Request` - Missing fields or password too short
- `409 Conflict` - Username or email already exists
- `500 Internal Server Error` - Server error

---

#### 2. Login

**Endpoint:** `POST /auth/login`

**Description:** Login to existing account.

**Request:**
```json
{
  "email": "john@example.com",
  "password": "securepassword123"
}
```

**Response:**
```json
{
  "message": "Login successful",
  "user": {
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com"
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error Responses:**
```json
{"error": "Email and password are required"}
{"error": "Invalid email or password"}
```

**Status Codes:**
- `200 OK` - Login successful
- `400 Bad Request` - Missing email or password
- `401 Unauthorized` - Invalid credentials
- `500 Internal Server Error` - Server error

---

#### 3. Get Profile

**Endpoint:** `GET /auth/profile`

**Description:** Get current user profile information.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "user": {
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "created_at": "2026-02-03 10:30:00",
    "total_images": 15
  }
}
```

**Status Codes:**
- `200 OK` - Profile retrieved successfully
- `401 Unauthorized` - Invalid or missing token
- `404 Not Found` - User not found

---

#### 4. Update Profile

**Endpoint:** `PUT /auth/profile`

**Description:** Update user profile information.

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```json
{
  "username": "new_username",
  "email": "newemail@example.com",
  "password": "newpassword123"
}
```

**Response:**
```json
{
  "message": "Profile updated successfully",
  "user": {
    "user_id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "new_username",
    "email": "newemail@example.com",
    "updated_at": "2026-02-03 11:45:00"
  }
}
```

**Error Responses:**
```json
{"error": "No data provided"}
{"error": "No valid fields to update"}
{"error": "Password must be at least 6 characters"}
{"error": "Username already exists"}
{"error": "Email already exists"}
```

**Status Codes:**
- `200 OK` - Profile updated successfully
- `400 Bad Request` - No data provided, no valid fields, or password too short
- `401 Unauthorized` - Invalid or missing token
- `409 Conflict` - Username or email already exists

---

### Image Generation Endpoints

#### 5. Health Check

**Endpoint:** `GET /health`

**Description:** Check the API health status, device information, and GFPGAN availability.

**Request:**
```http
GET /health HTTP/1.1
Host: localhost:5000
```

**Response:**
```json
{
  "status": "ok",
  "device": "cuda",
  "gfpgan_available": true
}
```

**Response Fields:**
- `status`: API health status (`ok` or `error`)
- `device`: Computation device (`cuda` for GPU, `cpu` for CPU)
- `gfpgan_available`: Whether GFPGAN enhancement is available (`true` or `false`)

**Status Codes:**
- `200 OK` - Service is healthy

**Example cURL:**
```bash
curl http://localhost:5000/health
```

---

#### 6. Generate Image

**Endpoint:** `POST /generate`

**Description:** Convert a single pencil sketch to a realistic image with optional GFPGAN enhancement. Requires authentication.

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```http
POST /generate HTTP/1.1
Host: localhost:5000
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="image"; filename="sketch.png"
Content-Type: image/png

[binary image data]
--boundary
Content-Disposition: form-data; name="attributes"

0.25,0.75,0.33,0.33
--boundary
Content-Disposition: form-data; name="enhancement"

gfpgan
--boundary
Content-Disposition: form-data; name="format"

image
--boundary--
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `image` | file | Yes | Pencil sketch image file (PNG, JPG, JPEG, BMP, WEBP). Max recommended size: 10MB |
| `attributes` | string | No | 4 comma-separated float values (0.0-1.0) for tone control: `skin,hair,eye,lip`. Default: `0.0,0.0,0.0,0.0` |
| `enhancement` | string | No | Enhancement method: `gfpgan` (AI-based face restoration) or `pil` (traditional filters). Default: `gfpgan` |
| `format` | string | No | Response format: `image` (PNG file) or `base64` (JSON). Default: `image` |
| `quality` | string | No | Quality preset for PIL enhancement: `low`, `medium`, `high`, `ultra`. Default: `medium`. Only used when `enhancement=pil` |
| `upscale` | string | No | Set to `true` to upscale output to 1024x1024. Default: `false` |
| `save` | string | No | Set to `true` to save image to history. Default: `false` |

**Image Validation:**

The API accepts the following image formats:
- **Supported formats:** PNG, JPG, JPEG, BMP, WEBP, TIFF
- **Recommended format:** PNG or JPG
- **Color mode:** Automatically converted to RGB
- **Size:** Any size (automatically resized to 512x512 for processing)
- **Max file size:** No hard limit, but 10MB recommended for performance
- **Aspect ratio:** Any (will be resized maintaining aspect ratio, then center-cropped to square)

**Image Preprocessing:**
1. Convert to RGB color mode
2. Apply median filter denoising (3x3 kernel)
3. Slight contrast enhancement (1.05x)
4. Resize to 512x512 pixels
5. Auto-masking to separate sketch from background

**Enhancement Methods:**

1. **GFPGAN Enhancement** (`enhancement=gfpgan`, default):
   - AI-based face restoration and enhancement
   - Significantly improves facial details, skin texture, and overall quality
   - Automatically downloads model weights on first use (~350MB)
   - Best for portrait sketches and faces
   - Falls back to PIL enhancement if GFPGAN unavailable
   - **Recommended for production use**

2. **PIL Enhancement** (`enhancement=pil`):
   - Traditional image processing filters
   - Sharpness, contrast, and color adjustments
   - Faster processing, no model download required
   - Quality controlled by `quality` parameter
   - Good for non-portrait sketches or when GFPGAN unavailable

**Response (format=image):**
- Content-Type: `image/png`
- Binary PNG image data

**Response (format=base64):**
```json
{
  "image": "iVBORw0KGgoAAAANSUhEUgAA...",
  "format": "base64",
  "quality": "medium",
  "enhancement": "gfpgan",
  "size": [512, 512],
  "image_id": "550e8400-e29b-41d4-a716-446655440000",
  "saved": true
}
```

**Response Fields:**
- `image`: Base64-encoded PNG image (only when `format=base64`)
- `format`: Response format (`base64`)
- `quality`: Quality preset used (only relevant for PIL enhancement)
- `enhancement`: Enhancement method used (`gfpgan` or `pil`)
- `size`: Output image dimensions `[width, height]`
- `image_id`: Unique ID of saved image (only when `save=true`)
- `saved`: Whether image was saved to history (only when `save=true`)

**Error Responses:**
```json
{"error": "No image file provided"}
{"error": "Empty filename"}
{"error": "Attributes must be 4 values"}
{"error": "Token is missing"}
{"error": "Token has expired"}
{"error": "Invalid token"}
```

**Status Codes:**
- `200 OK` - Image generated successfully
- `400 Bad Request` - Missing or invalid parameters
- `401 Unauthorized` - Invalid or missing token
- `500 Internal Server Error` - Processing error

**Example cURL (GFPGAN Enhancement):**
```bash
# Generate with GFPGAN enhancement (default)
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "attributes=0.25,0.75,0.33,0.33" \
  -F "enhancement=gfpgan" \
  -F "format=image" \
  --output result.png

# Generate with GFPGAN and upscaling
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.jpg" \
  -F "enhancement=gfpgan" \
  -F "upscale=true" \
  --output result_hd.png

# Generate with PIL enhancement (fallback)
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "enhancement=pil" \
  -F "quality=high" \
  --output result.png

# Return base64 JSON and save to history
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "format=base64" \
  -F "save=true"
```

**Example Python:**
```python
import requests

BASE_URL = 'http://localhost:5000'
headers = {'Authorization': f'Bearer {token}'}

# Generate with GFPGAN
with open('sketch.png', 'rb') as f:
    files = {'image': f}
    data = {
        'attributes': '0.25,0.75,0.33,0.33',
        'enhancement': 'gfpgan',
        'format': 'base64',
        'save': 'true'
    }
    response = requests.post(f'{BASE_URL}/generate', 
                            files=files, data=data, headers=headers)
    result = response.json()
    print(f"Enhancement used: {result['enhancement']}")
    print(f"Image ID: {result['image_id']}")
```

**Example JavaScript:**
```javascript
const formData = new FormData();
formData.append('image', fileInput.files[0]);
formData.append('attributes', '0.25,0.75,0.33,0.33');
formData.append('enhancement', 'gfpgan');
formData.append('format', 'base64');
formData.append('save', 'true');

const response = await fetch('http://localhost:5000/generate', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${token}` },
  body: formData
});

const data = await response.json();
console.log('Enhancement:', data.enhancement);
console.log('Image:', data.image); // base64 string
```

**Performance Notes:**
- **GFPGAN first run:** Downloads model weights (~350MB), takes 30-60 seconds
- **GFPGAN subsequent runs:** 2-5 seconds per image (CPU), <1 second (GPU)
- **PIL enhancement:** <1 second per image
- **Upscaling:** Adds ~0.5 seconds processing time
- **GPU acceleration:** Significantly faster with CUDA-enabled GPU

**Troubleshooting:**
- If GFPGAN fails to load, API automatically falls back to PIL enhancement
- Check `/health` endpoint to verify `gfpgan_available: true`
- Ensure sufficient disk space for model weights (~350MB)
- For CPU-only systems, GFPGAN may be slower; consider using `enhancement=pil`

---

#### 7. Generate Batch Images

**Endpoint:** `POST /generate-batch`

**Description:** Convert multiple pencil sketches to realistic images in a single request. Requires authentication.

**Headers:**
```
Authorization: Bearer <token>
```

**Request:**
```http
POST /generate-batch HTTP/1.1
Host: localhost:5000
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="images"; filename="sketch1.png"
Content-Type: image/png

[binary image data]
--boundary
Content-Disposition: form-data; name="images"; filename="sketch2.png"
Content-Type: image/png

[binary image data]
--boundary
Content-Disposition: form-data; name="attributes"

0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
--boundary--
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `images` | file[] | Yes | Multiple pencil sketch image files |
| `attributes` | string | No | 4 comma-separated float values (0.0-1.0) for tone control: `skin,hair,eye,lip`. Optional - model works without it |
| `save` | string | No | Set to `true` to save all images to history. Default: `false` |
| `quality` | string | No | Quality preset: `low`, `medium`, `high`, `ultra`. Default: `medium` |
| `upscale` | string | No | Set to `true` to upscale outputs to 1024x1024. Default: `false` |

**Response:**
```json
{
  "results": [
    {
      "filename": "sketch1.png",
      "image": "iVBORw0KGgoAAAANSUhEUgAA...",
      "quality": "medium",
      "size": [256, 256],
      "image_id": "550e8400-e29b-41d4-a716-446655440000",
      "saved": true
    },
    {
      "filename": "sketch2.png",
      "image": "iVBORw0KGgoAAAANSUhEUgAA...",
      "quality": "medium",
      "size": [256, 256],
      "image_id": "660e8400-e29b-41d4-a716-446655440001",
      "saved": true
    }
  ],
  "count": 2
}
```

**Note:** `image_id` and `saved` fields only appear when `save=true`. The `size` field returns `[512, 512]` when `upscale=true`.

**Error Response:**
```json
{
  "error": "No images provided"
}
```

**Status Codes:**
- `200 OK` - Images generated successfully
- `400 Bad Request` - Missing or invalid parameters
- `401 Unauthorized` - Invalid or missing token
- `500 Internal Server Error` - Processing error

**Example cURL:**
```bash
curl -X POST http://localhost:5000/generate-batch \
  -H "Authorization: Bearer <token>" \
  -F "images=@sketch1.png" \
  -F "images=@sketch2.png" \
  -F "images=@sketch3.png" \
  -F "attributes=0.4,0.6,0.3,0.5" \
  -F "save=true"
```

---

### Image History Endpoints

#### 8. Get History

**Endpoint:** `GET /history`

**Description:** Get paginated list of user's generated images.

**Headers:**
```
Authorization: Bearer <token>
```

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | integer | 1 | Page number |
| `limit` | integer | 10 | Items per page |

**Response:**
```json
{
  "images": [
    {
      "image_id": "550e8400-e29b-41d4-a716-446655440000",
      "original_filename": "sketch1.png",
      "attributes": "1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0",
      "created_at": "2026-02-03 10:30:00"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 15,
    "pages": 2
  }
}
```

**Status Codes:**
- `200 OK` - History retrieved successfully
- `401 Unauthorized` - Invalid or missing token

**Example cURL:**
```bash
curl -X GET "http://localhost:5000/history?page=1&limit=10" \
  -H "Authorization: Bearer <token>"
```

---

#### 9. Get History Image

**Endpoint:** `GET /history/<image_id>`

**Description:** Download a specific generated image from history.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
- Content-Type: `image/png`
- Binary PNG image data

**Status Codes:**
- `200 OK` - Image retrieved successfully
- `401 Unauthorized` - Invalid or missing token
- `404 Not Found` - Image not found

**Example cURL:**
```bash
curl -X GET http://localhost:5000/history/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <token>" \
  --output downloaded_image.png
```

---

#### 10. Delete History Image

**Endpoint:** `DELETE /history/<image_id>`

**Description:** Delete a specific image from history.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "message": "Image deleted successfully"
}
```

**Status Codes:**
- `200 OK` - Image deleted successfully
- `401 Unauthorized` - Invalid or missing token
- `404 Not Found` - Image not found

**Example cURL:**
```bash
curl -X DELETE http://localhost:5000/history/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer <token>"
```

---

#### 11. Clear History

**Endpoint:** `DELETE /history`

**Description:** Delete all images from user's history.

**Headers:**
```
Authorization: Bearer <token>
```

**Response:**
```json
{
  "message": "History cleared successfully",
  "deleted_count": 15
}
```

**Status Codes:**
- `200 OK` - History cleared successfully
- `401 Unauthorized` - Invalid or missing token

**Example cURL:**
```bash
curl -X DELETE http://localhost:5000/history \
  -H "Authorization: Bearer <token>"
```

---

## Quality Settings

The API includes post-processing enhancements to improve output quality:

**Quality Presets:**

| Preset | Sharpness | Contrast | Color | UnsharpMask | Use Case |
|--------|-----------|----------|-------|-------------|----------|
| `low` | 1.0x | 1.0x | 1.1x | Applied | Fast processing, preview |
| `medium` | 1.3x | 1.1x | 1.1x | Applied | Balanced (default) |
| `high` | 1.5x | 1.2x | 1.1x | Applied | Better details |
| `ultra` | 1.8x | 1.3x | 1.1x | Applied | Maximum quality |

**Additional Post-Processing (all presets):**
- Color enhancement: 1.1x applied uniformly
- UnsharpMask filter: radius=2, percent=150, threshold=3

**Input Preprocessing (all requests):**
- Median filter denoising (3x3 kernel)
- Slight contrast enhancement (1.05x)

**Upscaling:**
- Set `upscale=true` to output 512x512 images (2x resolution)
- Uses high-quality LANCZOS resampling
- Provides 4x more pixels for better detail

**Example:**
```bash
# Ultra quality with upscaling
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "quality=ultra" \
  -F "upscale=true" \
  -F "format=base64"
```

---

## Attributes

The new model architecture uses a 4-channel input (RGB + Mask) and accepts 4 floating-point tone attributes:

1. **skin_tone** (0.0-1.0): Skin tone control
2. **hair_tone** (0.0-1.0): Hair tone control  
3. **eye_tone** (0.0-1.0): Eye tone control
4. **lip_tone** (0.0-1.0): Lip tone control

**Default values:** `[0.25, 0.75, 0.33, 0.33]`

The `attributes` parameter is optional. If not provided, the model uses zero values `[0.0, 0.0, 0.0, 0.0]` which allows the model to generate images based purely on the sketch input without tone conditioning.

**Note:** Attributes were used during training but are optional during inference. The model can generate high-quality images without explicit attribute values.

---

## Error Handling

All endpoints return JSON error responses with appropriate HTTP status codes:

```json
{
  "error": "Error message description"
}
```

**Common Errors:**
- `Token is missing` (401) - No Authorization header provided
- `Token has expired` (401) - JWT token has expired (7 days)
- `Invalid token` (401) - Malformed or invalid JWT token
- `No image file provided` (400) - Missing image in request
- `Empty filename` (400) - File uploaded without name
- `Attributes must be 4 values (skin_tone, hair_tone, eye_tone, lip_tone)` (400) - Invalid attribute count
- `No images provided` (400) - Missing images in batch request
- `Username, email, and password are required` (400) - Missing signup fields
- `Email and password are required` (400) - Missing login fields
- `Password must be at least 6 characters` (400) - Password too short
- `Username or email already exists` (409) - Duplicate user registration
- `Username already exists` (409) - Username taken (profile update)
- `Email already exists` (409) - Email taken (profile update)
- `No data provided` (400) - Empty profile update request
- `No valid fields to update` (400) - No recognized fields in update
- `Invalid email or password` (401) - Login credentials incorrect
- `User not found` (404) - User account not found
- `Image not found` (404) - Requested image doesn't exist or doesn't belong to user
- Internal processing errors (500) - Model or image processing failures

---

## Image Processing

**Input Requirements:**
- Format: Any PIL-supported format (PNG, JPG, etc.)
- Recommended: RGB images
- The API automatically resizes images to 512x512 pixels
- Auto-masking is applied to separate sketch from background

**Output:**
- Format: PNG
- Size: 512x512 pixels (1024x1024 with upscale=true)
- Color space: RGB
- Post-processing: Sharpening, contrast enhancement, color boost (based on quality preset)

**Processing Pipeline:**
1. Image loaded and converted to RGB
2. **Preprocessing:** Median filter denoising (3x3) and slight contrast enhancement (1.05x)
3. Resized to 256x256
4. Normalized to [-1, 1] range
5. Passed through UNet Generator with attributes
6. Denormalized to [0, 1] range
7. **Post-processing:** Sharpness, contrast, color enhancement (1.1x), and UnsharpMask filter (radius=2, percent=150, threshold=3)
8. Optional upscaling to 512x512 via LANCZOS resampling
9. Converted to PNG format

---

## Model Architecture

**Type:** UNet Generator with skip connections

**Encoder Layers:**
- 8 downsampling blocks
- Progressive channel increase: 64 → 128 → 256 → 512
- Instance normalization and LeakyReLU activation

**Decoder Layers:**
- 8 upsampling blocks with skip connections
- Bilinear upsampling
- Instance normalization and ReLU activation
- Dropout in early layers (0.5)

**Attribute Integration:**
- 4-dimensional tone attribute vector (skin, hair, eye, lip)
- Concatenated at bottleneck layer (d8)
- Optional - can use zero values for pure sketch-based generation

---

## Rate Limits

Currently, no rate limits are enforced. For production use, consider implementing:
- Request rate limiting
- File size limits
- Concurrent request limits

---

## CORS

CORS is enabled for all origins. The API accepts requests from any domain.

---

## Database

**Type:** SQLite
**File:** `pencil2pixel.db`

**Tables:**

1. **users**
   - `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
   - `user_id` (TEXT, UNIQUE, NOT NULL) - UUID
   - `username` (TEXT, UNIQUE, NOT NULL)
   - `email` (TEXT, UNIQUE, NOT NULL)
   - `password_hash` (TEXT, NOT NULL)
   - `created_at` (TIMESTAMP, DEFAULT CURRENT_TIMESTAMP)
   - `updated_at` (TIMESTAMP, DEFAULT CURRENT_TIMESTAMP)

2. **image_history**
   - `id` (INTEGER, PRIMARY KEY, AUTOINCREMENT)
   - `image_id` (TEXT, UNIQUE, NOT NULL) - UUID
   - `user_id` (TEXT, NOT NULL, FOREIGN KEY → users.user_id, ON DELETE CASCADE)
   - `original_filename` (TEXT)
   - `generated_image` (BLOB, NOT NULL)
   - `attributes` (TEXT)
   - `created_at` (TIMESTAMP, DEFAULT CURRENT_TIMESTAMP)

**Note:** Database is automatically initialized on first run.

---

## Running the API

**Install Dependencies:**
```bash
pip install -r requirements.txt
```

**Set Environment Variables (Optional):**
```bash
export SECRET_KEY="your-secret-key-for-jwt"
```

**Start Server:**
```bash
python app.py
```

**Server Configuration:**
- Host: `0.0.0.0` (all interfaces)
- Port: `5000`
- Debug: Enabled

**Requirements:**
- Python 3.7+
- PyTorch
- Flask
- Flask-CORS
- Pillow
- torchvision
- PyJWT

---

## Postman Collection

A Postman collection is available for testing:
- **Workspace:** Pencil2Pixel API Testing
- **Collection:** Pencil2Pixel API
- **Environment:** Local Development

**Collection ID:** `36310029-f1c6bf23-f7b1-48ea-81a6-f2ee2f55264e`

---

## Examples

### Python Example
```python
import requests

BASE_URL = 'http://localhost:5000'

# Signup
signup_data = {
    'username': 'john_doe',
    'email': 'john@example.com',
    'password': 'securepass123'
}
response = requests.post(f'{BASE_URL}/auth/signup', json=signup_data)
token = response.json()['token']
print(f"Token: {token}")

# Headers with authentication
headers = {'Authorization': f'Bearer {token}'}

# Health check (no auth required)
response = requests.get(f'{BASE_URL}/health')
print(response.json())

# Get profile
response = requests.get(f'{BASE_URL}/auth/profile', headers=headers)
print(response.json())

# Generate single image and save to history
with open('sketch.png', 'rb') as f:
    files = {'image': f}
    data = {
        'attributes': '1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0',
        'format': 'base64',
        'save': 'true'
    }
    response = requests.post(f'{BASE_URL}/generate', files=files, data=data, headers=headers)
    result = response.json()
    print(f"Generated image ID: {result['image_id']}")

# Get history
response = requests.get(f'{BASE_URL}/history?page=1&limit=10', headers=headers)
history = response.json()
print(f"Total images: {history['pagination']['total']}")

# Download specific image
image_id = history['images'][0]['image_id']
response = requests.get(f'{BASE_URL}/history/{image_id}', headers=headers)
with open('downloaded.png', 'wb') as f:
    f.write(response.content)

# Delete specific image
response = requests.delete(f'{BASE_URL}/history/{image_id}', headers=headers)
print(response.json())

# Generate batch
files = [
    ('images', open('sketch1.png', 'rb')),
    ('images', open('sketch2.png', 'rb'))
]
data = {
    'attributes': '1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0',
    'save': 'true'
}
response = requests.post(f'{BASE_URL}/generate-batch', files=files, data=data, headers=headers)
print(f"Generated {response.json()['count']} images")
```

### JavaScript Example
```javascript
const BASE_URL = 'http://localhost:5000';
let token = '';

// Signup
async function signup() {
  const response = await fetch(`${BASE_URL}/auth/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: 'john_doe',
      email: 'john@example.com',
      password: 'securepass123'
    })
  });
  const data = await response.json();
  token = data.token;
  console.log('Token:', token);
}

// Login
async function login() {
  const response = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'john@example.com',
      password: 'securepass123'
    })
  });
  const data = await response.json();
  token = data.token;
  console.log('Logged in:', data.user);
}

// Get profile
async function getProfile() {
  const response = await fetch(`${BASE_URL}/auth/profile`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const data = await response.json();
  console.log('Profile:', data.user);
}

// Generate single image and save
async function generateImage(file) {
  const formData = new FormData();
  formData.append('image', file);
  formData.append('attributes', '1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0');
  formData.append('format', 'base64');
  formData.append('save', 'true');

  const response = await fetch(`${BASE_URL}/generate`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` },
    body: formData
  });
  const data = await response.json();
  console.log('Generated:', data.image_id);
  return data;
}

// Get history
async function getHistory(page = 1, limit = 10) {
  const response = await fetch(`${BASE_URL}/history?page=${page}&limit=${limit}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const data = await response.json();
  console.log('History:', data);
  return data;
}

// Delete image
async function deleteImage(imageId) {
  const response = await fetch(`${BASE_URL}/history/${imageId}`, {
    method: 'DELETE',
    headers: { 'Authorization': `Bearer ${token}` }
  });
  const data = await response.json();
  console.log('Deleted:', data.message);
}

// Usage
await signup();
await generateImage(fileInput.files[0]);
await getHistory();
```

---

## Security Considerations

- **Password Requirements:** Minimum 6 characters
- **Token Expiration:** 7 days
- **Password Storage:** Hashed using Werkzeug's security functions
- **CORS:** Enabled for all origins (configure for production)
- **Secret Key:** Set via environment variable `SECRET_KEY` for production

**Production Recommendations:**
- Use HTTPS
- Set strong SECRET_KEY
- Configure CORS for specific origins
- Implement rate limiting
- Add input validation and sanitization
- Use production-grade database (PostgreSQL, MySQL)
- Implement file size limits
- Add logging and monitoring

---

## Support

For issues or questions:
- Check the model file exists at `model/pencil2pixel.pth`
- Verify CUDA availability for GPU acceleration
- Ensure all dependencies are installed
- Check database file `pencil2pixel.db` is created
- Verify JWT token is valid and not expired
- Check server logs for detailed error messages

---

**Version:** 2.1.0  
**Last Updated:** February 10, 2026
