# Scout

![Scout space view showing nearby places and a walking route](README_IMAGES/SpaceView.png)

I made Scout to help me find the perfect apartment location in Madrid using the city's open data sources. It helps study-abroad students compare apartments based on university commute times and access to everyday essentials. Save potential homes, explore the neighborhood, and compare how each location fits your routine.

## Features

- **Apartment shortlist:** Save potential homes and compare their scores in one dashboard.
- **Nearby essentials:** Find grocery stores, gyms, and transit stops on an interactive map.
- **Travel details:** Explore walking and public transit routes with calculated travel times.
- **Weighted scoring:** Compare livability based on how often you visit each type of destination.

## Tech Stack

- **Frontend:** React, TypeScript, Vite, Tailwind CSS, TanStack Query, and Leaflet.
- **Backend:** Node.js, Express, and TypeScript.
- **Database:** PostgreSQL on Neon with Drizzle ORM.
- **Location data:** Nominatim for geocoding and Overpass for nearby places.
- **Routing:** Self-hosted OpenTripPlanner with Madrid GTFS and OpenStreetMap data.
- **Authentication and protection:** JWT cookies and Arcjet.

## What it does

- Enter an apartment address to create a space and place it on the map.
- Discover nearby essentials and calculate travel times.
- Inspect routes and travel details for each location.
- Compare saved apartments using a combined livability score.

Routing currently focuses on Madrid.

## How it works

```mermaid
flowchart TD
    UI["React frontend"] <-->|"REST API with JWT cookies"| API["Express backend: discovery and scoring"]
    API -->|"Route progress via Socket.IO"| UI
    UI -->|"Map tiles"| CARTO["CARTO basemaps"]
    API <-->|"Address geocoding"| NOM["Nominatim API"]
    API <-->|"Nearby places"| OVER["Overpass API"]
    API <-->|"Drizzle ORM: users, spaces, places and routes"| DB["Neon PostgreSQL"]
    API -->|"Request protection"| ARC["Arcjet"]
    UI -.->|"Analytics when configured"| PH["PostHog"]
    API -.->|"Analytics when configured"| PH

    subgraph Docker["Docker Compose"]
        OTP["OpenTripPlanner: port 8080"]
        DATA["Mounted data: Madrid OSM and GTFS feeds"]
        DATA -->|"Build and load routing graph"| OTP
    end

    API <-->|"GraphQL: walking and transit routes"| OTP
```

## Why I made it

I made Scout while searching for study-abroad housing. Checking the commute, nearby groceries, and neighborhood around every apartment meant repeating the same research across maps, spreadsheets, and notes. I wanted one place to do that work and compare the options. It also gave me a practical reason to build with geospatial data, public transit routing, and weighted scoring.

Made by Tejas Raman
