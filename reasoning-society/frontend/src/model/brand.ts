// A compile-time nominal brand: an intersection with a unique, uninhabited tag that erases to the
// bare primitive at runtime (ts-types "Brand a domain scalar"). The single shared `brand` symbol is
// distinguished per type by the string literal `B`, so `Brand<string, 'AgentId'>` and
// `Brand<string, 'CandidateId'>` are not interchangeable even though both erase to `string`.
declare const brand: unique symbol;

export type Brand<T, B extends string> = T & { readonly [brand]: B };
