import { useRef } from "react"

/**
 * Maintains a Map of ref instances keyed by a stable numeric id, handing out
 * the same setter function reference across renders for a given id. Passing
 * a fresh inline ref callback on every render (e.g. `ref={(el) => ...}`
 * inside a `.map()`) makes React tear down and rebuild every entry on each
 * re-render; caching one setter per id avoids that.
 */
export function useIndexedRefs<T>() {
  const refs = useRef(new Map<number, T>())
  const setters = useRef(new Map<number, (instance: T | null) => void>())

  function getRef(id: number) {
    let setter = setters.current.get(id)
    if (!setter) {
      setter = (instance: T | null) => {
        if (instance) refs.current.set(id, instance)
        else refs.current.delete(id)
      }
      setters.current.set(id, setter)
    }
    return setter
  }

  return { refs, getRef }
}
