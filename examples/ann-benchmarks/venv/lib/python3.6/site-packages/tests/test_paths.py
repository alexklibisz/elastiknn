# coding: utf-8
import unittest

from normality.paths import MAX_LENGTH, safe_filename


class PathsTest(unittest.TestCase):

    def test_safe_filename(self):
        self.assertEqual(None, safe_filename(None))
        self.assertEqual('test.txt', safe_filename('test.txt'))
        self.assertEqual('test.txt', safe_filename('test .txt'))
        self.assertEqual('test_bla.txt', safe_filename('test bla.txt'))
        self.assertEqual('test_bla.txt', safe_filename('test_bla.txt'))
        self.assertEqual('test_bla.txt', safe_filename('test.bla.txt'))
        self.assertEqual('test.txt', safe_filename('test', extension='txt'))

    def test_long_filename(self):
        long_name = ['long name'] * 100
        long_name = '-'.join(long_name)

        shortened = safe_filename(long_name)
        assert len(shortened) <= MAX_LENGTH, shortened

        shortened = safe_filename(long_name, extension='html')
        assert len(shortened) <= MAX_LENGTH, shortened

        shortened = safe_filename('bla', extension=long_name)
        assert len(shortened) <= MAX_LENGTH, shortened

        shortened = safe_filename(long_name, extension=long_name)
        assert len(shortened) <= MAX_LENGTH, shortened
