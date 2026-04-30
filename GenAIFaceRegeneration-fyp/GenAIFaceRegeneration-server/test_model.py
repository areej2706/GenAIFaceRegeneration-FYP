import torch
import torch.nn as nn
import torchvision.transforms as T
import numpy as np
from PIL import Image
import matplotlib.pyplot as plt
import os
import cv2

# Apply GFPGAN compatibility patch
try:
    import fix_gfpgan
except:
    pass

# --- ARCHITECTURE ---
class UltraGeneratorFinal(nn.Module):
    def __init__(self, attr_dim=4):
        super().__init__()
        def down(i, o, n=True):
            l = [nn.Conv2d(i, o, 4, 2, 1, bias=False)]
            if n: l.append(nn.InstanceNorm2d(o))
            l.append(nn.LeakyReLU(0.2, True))
            return nn.Sequential(*l)
        def up(i, o, dr=0.0):
            l = [nn.ConvTranspose2d(i, o, 4, 2, 1, bias=False), nn.InstanceNorm2d(o), nn.ReLU(True)]
            if dr: l.append(nn.Dropout(dr))
            return nn.Sequential(*l)

        self.d1 = down(3, 64, False)
        self.d2, self.d3, self.d4 = down(64, 128), down(128, 256), down(256, 512)
        self.d5, self.d6, self.d7, self.d8 = down(512, 512), down(512, 512), down(512, 512), down(512, 512, False)
        self.up1 = up(512 + attr_dim, 512, 0.5)
        self.up2, self.up3, self.up4 = up(1024, 512, 0.5), up(1024, 512, 0.5), up(1024, 512)
        self.up5, self.up6, self.up7 = up(1024, 256), up(512, 128), up(256, 64)

        self.final = nn.Sequential(
            nn.Upsample(scale_factor=2),
            nn.ZeroPad2d((1,0,1,0)),
            nn.Conv2d(128, 3, 4, padding=1),
            nn.Tanh()
        )

    def forward(self, s, a):
        d1 = self.d1(s); d2 = self.d2(d1); d3 = self.d3(d2); d4 = self.d4(d3)
        d5 = self.d5(d4); d6 = self.d6(d5); d7 = self.d7(d6); d8 = self.d8(d7)
        a_exp = a.view(a.size(0), -1, 1, 1).expand(-1, -1, d8.size(2), d8.size(3))
        u1 = self.up1(torch.cat([d8, a_exp], 1))
        u2 = self.up2(torch.cat([u1, d7], 1)); u3 = self.up3(torch.cat([u2, d6], 1))
        u4 = self.up4(torch.cat([u3, d5], 1)); u5 = self.up5(torch.cat([u4, d4], 1))
        u6 = self.up6(torch.cat([u5, d3], 1)); u7 = self.up7(torch.cat([u6, d2], 1))
        return self.final(torch.cat([u7, d1], 1))

# --- LOAD MODEL ---
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

generator = UltraGeneratorFinal(attr_dim=4).to(device)

# Try loading available model weights
model_files = ['model/pencil2pixel.pth', 'model/pencil2pixel1.pth']
loaded = False
for model_path in model_files:
    if os.path.exists(model_path):
        try:
            state_dict = torch.load(model_path, map_location=device)
            generator.load_state_dict(state_dict)
            generator.eval()
            print(f"✓ Loaded model: {model_path}")
            loaded = True
            break
        except Exception as e:
            print(f"✗ Failed to load {model_path}: {e}")

if not loaded:
    print("⚠ No model loaded - using random weights")

# --- LOAD GFPGAN ENHANCER ---
gfpgan_available = False
restorer = None

try:
    # Fix torchvision import issue
    import torchvision.transforms.functional as TF
    import sys
    if not hasattr(sys.modules.get('torchvision.transforms', None), 'functional_tensor'):
        sys.modules['torchvision.transforms.functional_tensor'] = TF
    
    from gfpgan import GFPGANer
    
    # Initialize GFPGAN
    restorer = GFPGANer(
        model_path='https://github.com/TencentARC/GFPGAN/releases/download/v1.3.0/GFPGANv1.3.pth',
        upscale=1,
        arch='clean',
        channel_multiplier=2,
        bg_upsampler=None,
        device=device
    )
    print("✓ GFPGAN enhancer loaded successfully")
    gfpgan_available = True
except ImportError as e:
    print(f"⚠ GFPGAN import error: {e}")
    print("  Try: pip install basicsr facexlib realesrgan")
except Exception as e:
    print(f"⚠ GFPGAN initialization error: {e}")
    print("  Continuing without enhancement...")

# --- TEST FUNCTION ---
def test_model(sketch_path, output_path="output.png", attributes=None, test_enhancement=True):
    """Test the model with a sketch image"""
    if not os.path.exists(sketch_path):
        print(f"Error: Sketch file not found: {sketch_path}")
        return
    
    # Default attributes if not provided
    if attributes is None:
        attributes = [0.25, 0.75, 0.33, 0.33]  # skin, hair, eye, lip tones
    
    # Prepare input
    sketch_pil = Image.open(sketch_path).convert('RGB')
    transform = T.Compose([
        T.Resize((512, 512)),
        T.ToTensor(),
        T.Normalize((0.5,), (0.5,))
    ])
    s_t = transform(sketch_pil).unsqueeze(0).to(device)
    a_t = torch.tensor([attributes], dtype=torch.float32).to(device)
    
    # Generate
    print(f"Generating image from {sketch_path}...")
    with torch.no_grad():
        fake_tensor = generator(s_t, a_t)
    
    # Denormalize to [0, 1]
    fake_np = (fake_tensor.squeeze(0).cpu().permute(1, 2, 0).numpy() + 1) / 2
    fake_np = np.clip(fake_np, 0, 1)
    
    # Convert to PIL for saving
    raw_output = Image.fromarray((fake_np * 255).astype(np.uint8))
    
    # Save raw output
    raw_output.save(output_path.replace('.png', '_raw.png'))
    print(f"✓ Saved raw output to: {output_path.replace('.png', '_raw.png')}")
    
    if test_enhancement and gfpgan_available:
        # Apply GFPGAN enhancement
        print("Applying GFPGAN enhancement...")
        img_bgr = cv2.cvtColor((fake_np * 255).astype(np.uint8), cv2.COLOR_RGB2BGR)
        _, _, restored_img = restorer.enhance(img_bgr, has_aligned=False, only_center_face=False, paste_back=True)
        restored_rgb = cv2.cvtColor(restored_img, cv2.COLOR_BGR2RGB)
        
        enhanced_output = Image.fromarray(restored_rgb)
        enhanced_output.save(output_path.replace('.png', '_enhanced.png'))
        print(f"✓ Saved GFPGAN enhanced output to: {output_path.replace('.png', '_enhanced.png')}")
        
        # Display comparison: Sketch | Raw | GFPGAN Enhanced
        fig, axes = plt.subplots(1, 3, figsize=(18, 6))
        axes[0].imshow(sketch_pil)
        axes[0].set_title("Input Sketch")
        axes[0].axis('off')
        axes[1].imshow(fake_np)
        axes[1].set_title("Raw GAN Output")
        axes[1].axis('off')
        axes[2].imshow(restored_rgb)
        axes[2].set_title("GFPGAN Enhanced")
        axes[2].axis('off')
        plt.tight_layout()
        plt.savefig(output_path.replace('.png', '_comparison.png'), dpi=150)
        print(f"✓ Saved comparison to: {output_path.replace('.png', '_comparison.png')}")
        plt.show()
    elif test_enhancement and not gfpgan_available:
        print("⚠ GFPGAN not available - skipping enhancement")
        # Display side-by-side without enhancement
        fig, axes = plt.subplots(1, 2, figsize=(12, 6))
        axes[0].imshow(sketch_pil)
        axes[0].set_title("Input Sketch")
        axes[0].axis('off')
        axes[1].imshow(raw_output)
        axes[1].set_title("Generated Image (No Enhancement)")
        axes[1].axis('off')
        plt.tight_layout()
        plt.savefig(output_path.replace('.png', '_comparison.png'))
        print(f"✓ Saved comparison to: {output_path.replace('.png', '_comparison.png')}")
        plt.show()
    else:
        # Display side-by-side without enhancement
        fig, axes = plt.subplots(1, 2, figsize=(12, 6))
        axes[0].imshow(sketch_pil)
        axes[0].set_title("Input Sketch")
        axes[0].axis('off')
        axes[1].imshow(raw_output)
        axes[1].set_title("Generated Image")
        axes[1].axis('off')
        plt.tight_layout()
        plt.savefig(output_path.replace('.png', '_comparison.png'))
        print(f"✓ Saved comparison to: {output_path.replace('.png', '_comparison.png')}")
        plt.show()

# --- RUN TESTS ---
if __name__ == "__main__":
    print("="*60)
    print("PENCIL2PIXEL MODEL TEST - WITH GFPGAN ENHANCEMENT")
    print("="*60)
    print(f"GFPGAN Available: {gfpgan_available}")
    print("="*60)
    
    # Test with available images
    test_images = [
        'images/sketch1.jpeg',
        'images/sketch2.jpeg',
        'images/sketch3.jpeg',
        'images/sketch4.jpeg'
    ]
    
    for i, img_path in enumerate(test_images, 1):
        if os.path.exists(img_path):
            print(f"\n{'='*60}")
            print(f"Test {i}/4: {img_path}")
            print('='*60)
            test_model(img_path, f"test_output_{i}.png", test_enhancement=True)
        else:
            print(f"Skipping {img_path} - file not found")
    
    print("\n" + "="*60)
    print("TEST COMPLETE!")
    print("="*60)
    if gfpgan_available:
        print("\nGenerated files:")
        print("  - test_output_X_raw.png (raw model output)")
        print("  - test_output_X_enhanced.png (with GFPGAN enhancement)")
        print("  - test_output_X_comparison.png (side-by-side comparison)")
        print("\nGFPGAN Enhancement:")
        print("  ✓ Face restoration and enhancement")
        print("  ✓ Detail preservation")
        print("  ✓ HD quality improvement")
    else:
        print("\nGFPGAN not available - only raw outputs generated")
        print("Install with: pip install gfpgan")
    print("="*60)
