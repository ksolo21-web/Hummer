#!/usr/bin/env python3
"""Preserve the approved TRELLIS lead faces without additive geometry.

Two reviewed attempts added modeled facial accents to Characters 1 and 2. Both made the
faces less faithful: the first produced oversized eyes and facial shapes, and the second
created duplicate floating glasses over details already present in the reconstructed mesh.
The recovered TRELLIS assets already contain the approved glasses, facial structure and
hair. The correct production behavior is therefore to preserve that mesh and texture
unchanged rather than stacking synthetic geometry over it.
"""

SCHEMA_VERSION = 3


def create_modeled_face_details(character, meshes, bounds):
    if character not in ("Character1", "Character2"):
        return {
            "schemaVersion": SCHEMA_VERSION,
            "applied": False,
            "reason": "lead-face preservation applies only to Characters 1 and 2",
        }, None

    return {
        "schemaVersion": SCHEMA_VERSION,
        "applied": False,
        "mode": "preserve recovered TRELLIS face without additive geometry",
        "source": "approved character turnaround sheet and recovered TRELLIS reconstruction",
        "preservedReconstructionFeatures": [
            "glasses",
            "eyes",
            "eyelids",
            "nose",
            "mouth",
            "facial hair",
            "cheeks",
            "skin texture",
            "hair silhouette",
        ],
        "rejectedStrategies": [
            "image-mapped portrait patch",
            "oversized modeled eye and mouth replacement",
            "duplicate floating glasses geometry",
        ],
        "reason": (
            "Human proof review found that every additive face layer reduced fidelity. "
            "The recovered textured head is preserved as the best available approved-reference result."
        ),
        "surfaceType": "original skinned textured reconstruction; no billboard; no portrait patch; no duplicate face geometry",
    }, None
