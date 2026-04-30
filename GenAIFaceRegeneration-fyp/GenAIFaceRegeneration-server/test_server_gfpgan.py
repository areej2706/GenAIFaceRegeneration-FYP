import requests
import json
from PIL import Image
import io
import base64

# Server URL
BASE_URL = "http://localhost:5000"

# Test 1: Check health and GFPGAN availability
print("="*60)
print("TEST 1: Health Check")
print("="*60)
response = requests.get(f"{BASE_URL}/health")
health_data = response.json()
print(f"Status: {health_data['status']}")
print(f"Device: {health_data['device']}")
print(f"GFPGAN Available: {health_data['gfpgan_available']}")

# Test 2: Signup/Login to get token
print("\n" + "="*60)
print("TEST 2: Authentication")
print("="*60)

# Try to login first
login_data = {
    "email": "test@example.com",
    "password": "test123456"
}

response = requests.post(f"{BASE_URL}/auth/login", json=login_data)
if response.status_code == 200:
    token = response.json()['token']
    print("✓ Logged in successfully")
elif response.status_code == 401:
    # Try different credentials
    login_data = {
        "email": "gfpgan_test@example.com",
        "password": "test123456"
    }
    response = requests.post(f"{BASE_URL}/auth/login", json=login_data)
    if response.status_code == 200:
        token = response.json()['token']
        print("✓ Logged in successfully")
    else:
        # Signup with new credentials
        signup_data = {
            "username": "gfpgan_testuser",
            "email": "gfpgan_test@example.com",
            "password": "test123456"
        }
        response = requests.post(f"{BASE_URL}/auth/signup", json=signup_data)
        if response.status_code == 201:
            token = response.json()['token']
            print("✓ Signed up successfully")
        else:
            print(f"✗ Authentication failed: {response.json()}")
            exit(1)
else:
    print(f"✗ Authentication failed: {response.json()}")
    exit(1)

headers = {'Authorization': f'Bearer {token}'}

# Test 3: Generate image with GFPGAN enhancement
print("\n" + "="*60)
print("TEST 3: Generate Image with GFPGAN")
print("="*60)

test_image = "images/sketch1.jpeg"
with open(test_image, 'rb') as f:
    files = {'image': f}
    data = {
        'quality': 'high',
        'enhancement': 'gfpgan',
        'format': 'base64',
        'attributes': '0.25,0.75,0.33,0.33'
    }
    
    print(f"Sending request with GFPGAN enhancement...")
    response = requests.post(f"{BASE_URL}/generate", files=files, data=data, headers=headers)
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ Generation successful!")
        print(f"  Quality: {result['quality']}")
        print(f"  Enhancement: {result['enhancement']}")
        print(f"  Size: {result['size']}")
        
        # Save the result
        img_data = base64.b64decode(result['image'])
        img = Image.open(io.BytesIO(img_data))
        img.save('server_test_gfpgan.png')
        print(f"✓ Saved output to: server_test_gfpgan.png")
    else:
        print(f"✗ Generation failed: {response.json()}")

# Test 4: Generate image with PIL enhancement (comparison)
print("\n" + "="*60)
print("TEST 4: Generate Image with PIL Enhancement")
print("="*60)

with open(test_image, 'rb') as f:
    files = {'image': f}
    data = {
        'quality': 'high',
        'enhancement': 'pil',
        'format': 'base64',
        'attributes': '0.25,0.75,0.33,0.33'
    }
    
    print(f"Sending request with PIL enhancement...")
    response = requests.post(f"{BASE_URL}/generate", files=files, data=data, headers=headers)
    
    if response.status_code == 200:
        result = response.json()
        print(f"✓ Generation successful!")
        print(f"  Quality: {result['quality']}")
        print(f"  Enhancement: {result['enhancement']}")
        print(f"  Size: {result['size']}")
        
        # Save the result
        img_data = base64.b64decode(result['image'])
        img = Image.open(io.BytesIO(img_data))
        img.save('server_test_pil.png')
        print(f"✓ Saved output to: server_test_pil.png")
    else:
        print(f"✗ Generation failed: {response.json()}")

print("\n" + "="*60)
print("ALL TESTS COMPLETE!")
print("="*60)
print("\nGenerated files:")
print("  - server_test_gfpgan.png (with GFPGAN enhancement)")
print("  - server_test_pil.png (with PIL enhancement)")
print("\nCompare the two images to see the difference!")
print("="*60)
