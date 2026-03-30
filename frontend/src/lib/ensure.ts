/**
 * Remove optionality and redefine as required but allow undefined.
 *
 * Example:
 * <pre>
 * interface Foo {
 *   foo?: number;
 * }
 * </pre>
 *
 * <pre>
 * type AllFooProps = OptionalToUndefined<Foo>;
 * </pre>
 * Now AllFooProps effectively looks like this:
 * <pre>
 * interface AllFooProps {
 *   foo: number | undefined;
 * }
 * </pre>
 *
 *
 * <p>
 *
 * FYI: the utility-type Record<Foo> would also remove undefined:
 * <pre>
 * type AllFooRecord = Record<Foo>;
 * </pre>
 * would result in:
 * <pre>
 * interface AllFooRecord {
 *   foo: number;
 * }
 * </pre>
 *
 * Idea stolen from: https://github.com/microsoft/TypeScript/issues/31025#issuecomment-484734942
 */
export type OptionalToUndefined<T> = {
  [P in keyof Required<T>]: OptionalToUndefined<T[P]>;
};
/**
 * See {@link OptionalToUndefined} but only for the root-level object.
 *
 * Types of nestes object stay untouched.
 */
export type OptionalToUndefinedFlat<T> = {
  [P in keyof Required<T>]: T[P];
};

/**
 * Make sure the programmer explicitly provides all properties of an object-literal, even the optional ones.
 *
 * Most common use-case: you want to declare + assign an object inline to a function
 * accepting any (like FormGroup.reset) but want to make sure,
 * that your argument conforms to a given type (like the datastructure the FormGroup is based upon).
 *
 * <strong>!!! Special Note !!!</strong>
 * Unfortunately, it is possible to leave out the "Wanted" type argument which passes compilation but ensures nothing :(
 */
export function ensureProps<Wanted>(
  value: OptionalToUndefined<Wanted>,
): OptionalToUndefined<Wanted> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return value as any;
}

/**
 * Same as {@link ensureProps} but only for the root-level object.
 *
 * Types of nestes object stay untouched.
 */
export function ensurePropsFlat<Wanted>(
  value: OptionalToUndefinedFlat<Wanted>,
): Wanted {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return value as any;
}

/**
 * This is a neat way to compile-time ensure (but not cast!) the proper type of e.g. anonymous inline-objects.
 *
 * Unlike {@link ensureProps}, optional properties (denoted by "?") stay optional.
 *
 * Usage:
 * <pre>{@code
 * interface Foo {
 *   bar: string
 * }
 *
 * const foo = ensureType<Foo>({bar: '123'});
 * }</pre>
 *
 * <strong>!!! Special Note !!!</strong>
 * Unfortunately, it is possible to leave out the "Wanted" type argument which passes compilation but ensures nothing :(
 */
export function ensureType<Wanted>(value: Wanted): Wanted {
  return value;
}
