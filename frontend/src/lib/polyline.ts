import type { PathOptions } from "leaflet"

// OTP returns Google-encoded polylines for each itinerary leg. Leaflet uses
// [latitude, longitude] tuples, which is also the order produced here.
export function decodePolyline(encoded: string): [number, number][] {
  const points: [number, number][] = []
  let index = 0
  let latitude = 0
  let longitude = 0

  const decodeValue = () => {
    let result = 0
    let shift = 0
    let byte: number

    do {
      byte = encoded.charCodeAt(index++) - 63
      result |= (byte & 0x1f) << shift
      shift += 5
    } while (byte >= 0x20 && index <= encoded.length)

    return result & 1 ? ~(result >> 1) : result >> 1
  }

  while (index < encoded.length) {
    latitude += decodeValue()
    longitude += decodeValue()
    points.push([latitude / 1e5, longitude / 1e5])
  }

  return points
}

export function routePathOptions(mode: string): PathOptions {
  switch (mode) {
    case "WALK":
      return { color: "#64748b", weight: 4, opacity: 0.85, dashArray: "6 8" }
    case "SUBWAY":
      return { color: "#2563eb", weight: 6, opacity: 0.9 }
    case "BUS":
      return { color: "#0d9488", weight: 6, opacity: 0.9 }
    case "TRAM":
      return { color: "#d97706", weight: 6, opacity: 0.9 }
    default:
      return { color: "#7c3aed", weight: 6, opacity: 0.9 }
  }
}
