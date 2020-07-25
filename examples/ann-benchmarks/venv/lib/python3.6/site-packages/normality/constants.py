
WS = ' '

# Unicode character classes, see:
# http://www.fileformat.info/info/unicode/category/index.htm
# https://en.wikipedia.org/wiki/Unicode_character_property
# http://www.unicode.org/charts/beta/script/
UNICODE_CATEGORIES = {
    'Cc': None,
    'Cf': None,
    'Cs': None,
    'Co': None,
    'Cn': None,
    'Lm': None,
    'Mn': None,
    'Mc': WS,
    'Me': None,
    'Zs': WS,
    'Zl': WS,
    'Zp': WS,
    'Pc': WS,
    'Pd': WS,
    'Ps': WS,
    'Pe': WS,
    'Pi': WS,
    'Pf': WS,
    'Po': WS,
    'Sc': None,
    'Sk': None,
    'So': None
}

CONTROL_CODES = {
    'Cc': WS,
    'Cf': WS,
    'Cs': WS,
    'Co': WS,
    'Cn': WS
}
