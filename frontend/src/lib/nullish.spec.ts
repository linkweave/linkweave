import {hasValue, isNullish, requireValue} from './nullish';

describe('nullish', () => {
  describe('isNullish', () => {
    it('should return true for undefined', () => {
      expect(isNullish(undefined)).toEqual(true);
    });

    it('should return true for null', () => {
      expect(isNullish(undefined)).toEqual(true);
    });

    ['', '0', 'asdf', 0, {}, [], new Date(), Infinity, -Infinity, NaN].forEach(
      (value) => {
        it(`should return false for all other values: ${value}`, () => {
          expect(isNullish(value)).toEqual(false);
        });
      },
    );
  });

  describe('hasValue', () => {
    it('should return false for undefined', () => {
      expect(hasValue(undefined)).toEqual(false);
    });

    it('should return false for null', () => {
      expect(hasValue(undefined)).toEqual(false);
    });

    it('should return true for all other values', () => {
      [
        '',
        '0',
        'asdf',
        0,
        {},
        [],
        new Date(),
        Infinity,
        -Infinity,
        NaN,
      ].forEach((value) => {
        expect(hasValue(value)).toEqual(true);
      });
    });
  });

  describe('requireValue', () => {
    describe('with given value', () => {
      ['', [], {}, 1, new Date(), NaN].forEach((param) => {
        it(`should just return the value: ${param}`, () => {
          expect(requireValue(param)).toBe(param);
        });
      });
    });

    describe('with nullish value', () => {
      [null, undefined].forEach((param) => {
        it(`should throw with value: ${param}`, () => {
          expect(() => requireValue(param)).toThrowError();
        });
      });

      it('should throw with description', () => {
        expect(() => requireValue(null, 'Hello Null')).toThrowError(
          'Hello Null',
        );
      });
    });
  });
});
