"""
GFPGAN Compatibility Patch for newer torchvision versions

This module patches the missing torchvision.transforms.functional_tensor module
that was removed in torchvision >= 0.13.0 but is still required by GFPGAN.

The functional_tensor module was merged into torchvision.transforms.functional,
so we create an alias to maintain backward compatibility.
"""

import sys
import torchvision.transforms.functional as F

# Create a mock module for functional_tensor
class FunctionalTensorModule:
    """Mock module that redirects to torchvision.transforms.functional"""
    
    def __getattr__(self, name):
        # Redirect all attribute access to the functional module
        return getattr(F, name)

# Inject the mock module into sys.modules
sys.modules['torchvision.transforms.functional_tensor'] = FunctionalTensorModule()

print("✓ Applied GFPGAN compatibility patch for torchvision.transforms.functional_tensor")
