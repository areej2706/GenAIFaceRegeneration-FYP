# Pencil2Pixel API Documentation

## Overview
The Pencil2Pixel API converts pencil sketches into realistic images using a UNet-based deep learning model. The API is built with Flask and supports both single and batch image processing.

**Base URL:** `http://localhost:5000`

**Model:** UNet Generator with 20 attribute dimensions
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

**Description:** Check the API health status and device information.

**Request:**
```http
GET /health HTTP/1.1
Host: localhost:5000
```

**Response:**
```json
{
  "status": "ok",
  "device": "cuda"
}
```

**Status Codes:**
- `200 OK` - Service is healthy

---

#### 6. Generate Image

**Endpoint:** `POST /generate`

**Description:** Convert a single pencil sketch to a realistic image. Requires authentication.

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

0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
--boundary
Content-Disposition: form-data; name="format"

image
--boundary--
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `image` | file | Yes | Pencil sketch image file (PNG, JPG, etc.) |
| `attributes` | string | No | 20 comma-separated integer values for controlling generation. Default: `1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0` |
| `format` | string | No | Response format: `image` (PNG file) or `base64` (JSON). Default: `image` |
| `save` | string | No | Set to `true` to save image to history. Default: `false` |
| `quality` | string | No | Quality preset: `low`, `medium`, `high`, `ultra`. Default: `medium` |
| `upscale` | string | No | Set to `true` to upscale output to 512x512. Default: `false` |

> **Note:** The model architecture includes an attribute conditioning vector, but due to InstanceNorm in the network, attributes have no measurable effect on the generated output. The parameter is retained for API compatibility.

**Response (format=image):**
- Content-Type: `image/png`
- Binary PNG image data

**Response (format=base64):**
```json
{
  "image": "iVBORw0KGgoAAAANSUhEUgAA...",
  "format": "base64",
  "quality": "medium",
  "size": [256, 256],
  "image_id": "550e8400-e29b-41d4-a716-446655440000",
  "saved": true
}
```

**Note:** `image_id` and `saved` fields only appear when `save=true`. The `size` field returns `[512, 512]` when `upscale=true`.

**Error Responses:**
```json
{"error": "No image file provided"}
{"error": "Empty filename"}
{"error": "Attributes must be 20 values"}
```

**Status Codes:**
- `200 OK` - Image generated successfully
- `400 Bad Request` - Missing or invalid parameters
- `401 Unauthorized` - Invalid or missing token
- `500 Internal Server Error` - Processing error

**Example cURL:**
```bash
# Using raw attributes
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "attributes=1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0" \
  -F "format=image" \
  --output result.png

# Return base64 JSON and save to history (uses default attributes)
curl -X POST http://localhost:5000/generate \
  -H "Authorization: Bearer <token>" \
  -F "image=@sketch.png" \
  -F "format=base64" \
  -F "save=true"
```

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
| `attributes` | string | No | 20 comma-separated integer values applied to all images. Default: `1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0` |
| `save` | string | No | Set to `true` to save all images to history. Default: `false` |
| `quality` | string | No | Quality preset: `low`, `medium`, `high`, `ultra`. Default: `medium` |
| `upscale` | string | No | Set to `true` to upscale outputs to 512x512. Default: `false` |

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
  -F "attributes=1,0,1,0,1,0,0,1,1,0,0,1,0,1,0,0,1,1,0,0" \
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

The model architecture accepts a 20-element binary attribute vector that is injected at the bottleneck layer of the UNet Generator. However, **testing has confirmed that attributes have no measurable effect on the generated output** — all attribute vectors produce byte-for-byte identical images. This is because the InstanceNorm layer normalizes out the 1×1 spatial signal from the attribute conditioning.

The `attributes` parameter is retained in the API for compatibility but can be safely ignored. Default values are used automatically if omitted.

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
- `Attributes must be 20 values` (400) - Invalid attribute count
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
- The API automatically resizes images to 256x256 pixels

**Output:**
- Format: PNG
- Size: 256x256 pixels
- Color space: RGB
- Normalization: Applied during processing, denormalized in output

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
- 20-dimensional attribute vector
- Concatenated at bottleneck layer
- Influences generation characteristics

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
