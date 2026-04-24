# Pencil2Pixel Flask API

Flask REST API for sketch-to-image generation using conditional U-Net.

## Setup

```bash
pip install -r requirements.txt
```

## Run Server

```bash
python app.py
```

Server runs on `http://localhost:5000`

## API Endpoints

### 1. Health Check
```bash
GET /health
```

**Response:**
```json
{
  "status": "ok",
  "device": "cuda"
}
```

### 2. Generate Image (Single)
```bash
POST /generate
```

**Parameters:**
- `image` (file, required): Sketch image file
- `attributes` (string, optional): 20 comma-separated binary values (e.g., "1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0")
- `format` (string, optional): "image" (default) or "base64"

**Example (cURL - returns image):**
```bash
curl -X POST http://localhost:5000/generate \
  -F "image=@sketch.jpg" \
  -F "attributes=1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0" \
  --output result.png
```

**Example (cURL - returns base64):**
```bash
curl -X POST http://localhost:5000/generate \
  -F "image=@sketch.jpg" \
  -F "format=base64" \
  -F "attributes=1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0"
```

**Example (Python):**
```python
import requests

with open('sketch.jpg', 'rb') as f:
    files = {'image': f}
    data = {
        'attributes': '1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0',
        'format': 'image'
    }
    response = requests.post('http://localhost:5000/generate', files=files, data=data)
    
    with open('output.png', 'wb') as out:
        out.write(response.content)
```

### 3. Generate Batch
```bash
POST /generate-batch
```

**Parameters:**
- `images` (files, required): Multiple sketch image files
- `attributes` (string, optional): 20 comma-separated binary values (applied to all images)

**Response:**
```json
{
  "results": [
    {
      "filename": "sketch1.jpg",
      "image": "base64_encoded_image..."
    }
  ],
  "count": 1
}
```

**Example (cURL):**
```bash
curl -X POST http://localhost:5000/generate-batch \
  -F "images=@sketch1.jpg" \
  -F "images=@sketch2.jpg" \
  -F "attributes=1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0"
```

## Attributes

The model accepts 20 binary attributes (0 or 1) that control facial features. Common attributes in face datasets:
- Gender, age, hair color, glasses, smile, etc.
- If not provided, defaults to all zeros

## Testing

Run the test script:
```bash
python test_api.py
```

Edit `test_api.py` to uncomment and test specific endpoints with your images.

## Mobile/App Integration

**For mobile apps (React Native, Flutter, etc.):**
- Use multipart/form-data for image upload
- Set `format=base64` to receive base64 string
- Decode base64 to display image

**Example (JavaScript/React Native):**
```javascript
const formData = new FormData();
formData.append('image', {
  uri: imageUri,
  type: 'image/jpeg',
  name: 'sketch.jpg'
});
formData.append('attributes', '1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0');
formData.append('format', 'base64');

fetch('http://localhost:5000/generate', {
  method: 'POST',
  body: formData
})
.then(res => res.json())
.then(data => {
  const imageUri = `data:image/png;base64,${data.image}`;
  // Display imageUri in Image component
});
```

## Production Deployment

For production, use a WSGI server:
```bash
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app
```

Or use Docker, AWS Lambda, Google Cloud Run, etc.
# pencil2pexel-server
