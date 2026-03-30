export type Nullish = null | undefined;
export type NotNullish<T> = Exclude<T, Nullish>;
export type NonNullishTypes =
  | string
  | number
  | boolean
  | symbol
  | bigint
  | object;

/**
 * Returns true for values === undefined or === null.
 *
 * The inverse of {@link hasValue}.
 */
export function isNullish(value: unknown): value is Nullish {
  return value === undefined || value === null;
}

/**
 * Returns true for values !== undefined and !== null (i.e.: the inverse of {@link isNullish}.
 */
export function isNotNullish<T>(value: T): value is NotNullish<T> {
  return !isNullish(value);
}

/**
 * Convenience (for lighter reading): same as isNotEmpty (i.e.: the inverse of {@link isNullish}.
 */
export function hasValue<T>(value: T): value is NotNullish<T> {
  return isNotNullish(value);
}

export function isNullishOrEmpty<T>(someArray: Array<T> | undefined): boolean {
  return isNullish(someArray) || someArray.length === 0;
}

/**
 * Convenience: throws an error if the given value is nullish, returns the value otherwise.
 * @throws Error if the given value is nullish.
 * @returns the given value if it is not nullish.
 */
export function requireValue<T>(
  value: T,
  description?: string | (() => string),
): NotNullish<T> {
  if (hasValue(value)) {
    return value;
  }

  if (hasValue(description)) {
    const text =
      typeof description === 'function' ? description() : description;
    throw new Error(text);
  }

  // Angular sometimes shortens the *logged* stacktrace on service initialization,
  // so gather the real trace here and log it.
  const stack = new Error().stack;
  const desc = `value expected but none given: ${value}, stack: ${stack}`;
  throw new Error(desc);
}
