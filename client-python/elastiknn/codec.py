"""Mixin for API objects that can be converted to a dict compatible with the Elastiknn Json API."""

from typing import Dict


class Codec(object):
    """Mixin for API objects that can be converted to a dict compatible with the Elastiknn Json API."""

    def to_dict(self) -> Dict:
        """Return a dictionary compatible with the Elastiknn Json API."""
        raise NotImplementedError

