import {ensureProps, ensurePropsFlat, ensureType} from './ensure';

describe('ensure', () => {
  interface Bar {
    nestedString: string;
    nestedNumber: number;
    nestedOptional?: string;
  }

  interface Foo {
    aString: string;
    aNumber: number;
    optionalBar?: Bar;
  }

  describe('ensurePropsFlat', () => {
    it('accepts having all properties set', () => {
      ensurePropsFlat<Foo>({
        aString: 'asdf',
        aNumber: 123,
        optionalBar: {
          nestedString: 'asdf',
          nestedNumber: 123,
          nestedOptional: 'asdf',
        },
      });
    });

    it('accepts having all properties set with the optional property explicitly set to undefined', () => {
      ensurePropsFlat<Foo>({
        aString: 'asdf',
        aNumber: 123,
        optionalBar: undefined,
      });
    });

    it('rejects with an optional property missing', () => {
      // @ts-expect-error this is exactly what we want to test
      ensurePropsFlat<Foo>({
        aString: 'asdf',
        aNumber: 123,
        // optionalBar is missing
      });
    });
  });

  describe('ensureProps', () => {
    it('accepts having all properties set', () => {
      ensureProps<Foo>({
        aString: 'asdf',
        aNumber: 123,
        optionalBar: {
          nestedString: 'asdf',
          nestedNumber: 123,
          nestedOptional: 'asdf',
        },
      });
    });

    it('accepts having all properties set, the optional property set to undefined', () => {
      ensureProps<Foo>({
        aString: 'asdf',
        aNumber: 123,
        optionalBar: undefined,
      });
    });

    it('accepts having all properties set, the *nested* optional property set to undefined', () => {
      ensureProps<Foo>({
        aString: 'asdf',
        aNumber: 123,
        optionalBar: {
          nestedString: 'asdf',
          nestedNumber: 123,
          nestedOptional: undefined,
        },
      });
    });

    it('rejects with an optional property missing', () => {
      // @ts-expect-error this is exactly what we want to test
      ensureProps<Foo>({
        aString: 'asdf',
        aNumber: 123,
        // optionalBar is missing
      });
    });

    it('rejects with an optional nested property missing', () => {
      ensureProps<Foo>({
        aString: 'asdf',
        aNumber: 123,
        // @ts-expect-error this is exactly what we want to test
        optionalBar: {
          nestedString: 'asdf',
          nestedNumber: 123,
          // nestedOptional is missing
        },
      });
    });
  });

  describe('ensureType', () => {
    it('returns the same object', () => {
      const foo: Foo = {
        aString: 'asdf',
        aNumber: 123,
      };

      expect(ensureType<Foo>(foo)).toBe(foo);
    });

    it('throws an error if the object has additional properties', () => {
      ensureType<Foo>({
        aString: 'asdf',
        aNumber: 123,
        // @ts-expect-error this is exactly what we want to test
        additional: 'property',
      });
    });
  });
});
