import L from "leaflet"

export type PoiCategoryGroup = "transit" | "amenities"

export function getPoiCategoryGroup(category: string): PoiCategoryGroup {
  return category === "transit" ? "transit" : "amenities"
}

const ICONS: Record<PoiCategoryGroup, L.DivIcon> = {
  transit: L.divIcon({
    className: "",
    html: '<span style="display:block;width:12px;height:12px;border-radius:9999px;background:var(--info);border:2px solid var(--background);box-shadow:0 0 0 1px rgba(0,0,0,0.2);"></span>',
    iconSize: [12, 12],
    iconAnchor: [6, 6],
  }),
  amenities: L.divIcon({
    className: "",
    html: '<span style="display:block;width:12px;height:12px;border-radius:9999px;background:var(--warning);border:2px solid var(--background);box-shadow:0 0 0 1px rgba(0,0,0,0.2);"></span>',
    iconSize: [12, 12],
    iconAnchor: [6, 6],
  }),
}

const SELECTED_ICONS: Record<PoiCategoryGroup, L.DivIcon> = {
  transit: L.divIcon({
    className: "",
    html: '<span style="display:block;width:18px;height:18px;border-radius:9999px;background:var(--info);border:3px solid var(--primary);box-shadow:0 0 0 2px var(--background), 0 2px 6px rgba(0,0,0,0.35);"></span>',
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  }),
  amenities: L.divIcon({
    className: "",
    html: '<span style="display:block;width:18px;height:18px;border-radius:9999px;background:var(--warning);border:3px solid var(--primary);box-shadow:0 0 0 2px var(--background), 0 2px 6px rgba(0,0,0,0.35);"></span>',
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  }),
}

export function getPoiIcon(category: string, isSelected = false): L.DivIcon {
  const group = getPoiCategoryGroup(category)
  return isSelected ? SELECTED_ICONS[group] : ICONS[group]
}
