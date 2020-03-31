from typing import Dict


class Codec(object):

    def to_dict(self):
        raise NotImplementedError

    @staticmethod
    def from_dict(d: Dict):
        raise NotImplementedError
