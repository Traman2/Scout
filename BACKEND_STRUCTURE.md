# Backend folder architecture

This project uses a **layered backend structure** with Express and TypeScript. Files are grouped by responsibility, and matching feature names connect them across folders. For example, authentication has an `auth.routes.ts`, `auth.controller.ts`, and `auth.service.ts`.

## Folder layout

```text
backend/
├── src/
│   ├── index.ts        # Loads environment variables and starts the server
│   ├── server.ts       # HTTP server, Socket.IO, and shutdown handling
│   ├── app.ts          # Express setup, global middleware, and route mounting
│   ├── routes/         # Endpoint paths and route-level middleware
│   ├── controllers/    # Request handling and HTTP responses
│   ├── services/       # Business logic and database queries
│   ├── models/         # Database table definitions and relationships
│   ├── validations/    # Request validation schemas
│   ├── middleware/     # Shared request checks, such as authentication
│   ├── clients/        # Wrappers around external APIs
│   ├── configs/        # Database, logging, analytics, and security setup
│   ├── utils/          # Small reusable helpers
│   └── types/          # Shared TypeScript declarations
├── drizzle/            # Database migrations and migration metadata
├── drizzle.config.ts   # Migration tool configuration
├── package.json        # Dependencies, scripts, and import aliases
└── tsconfig.json       # TypeScript configuration
```

Tests live beside the files they cover, using names such as `space.service.test.ts`.

## How a request moves through the backend

**App middleware → Route → Controller → Service → Database or external API**

The controller sends the service's result back as an HTTP response.

| Layer | Responsibility |
| --- | --- |
| Routes | Match an HTTP method and URL to a controller, and attach checks such as required authentication. |
| Controllers | Read request data, validate it using schemas from `validations/`, call services, and choose response bodies and status codes. |
| Services | Apply business rules, query the database using models, and coordinate external API calls through clients. |
| Models | Define database tables, columns, constraints, and relationships using Drizzle ORM. |
| Clients | Handle communication with outside services, keeping API-specific details out of business logic. |

The main boundary is simple: **controllers handle HTTP; services handle application behavior**. Services receive ordinary values rather than Express request and response objects. Models describe the database; database queries live in services in this project, without a separate repository layer.

## Adding a feature in a new project

For a feature such as projects, use the same name across the relevant folders:

- `routes/project.routes.ts` defines the endpoints.
- `controllers/project.controller.ts` handles requests and responses.
- `services/project.service.ts` contains business logic and queries.
- `models/project.model.ts` defines any new database tables.
- `validations/project.validations.ts` defines accepted request data.

Mount the router in `app.ts`. If the feature changes the database schema, generate a migration in `drizzle/`. Add a client only if it needs an external API.

As a feature grows, split files by clear sub-responsibilities. This project keeps one spaces router while using separate controllers for spaces, points of interest, and routes.

## Conventions worth carrying over

- Keep startup, Express configuration, and server lifecycle in separate files.
- Keep controllers focused on HTTP and services independent of Express.
- Put shared authentication checks in middleware and input schemas in validations.
- Centralize infrastructure setup in configs and external integrations in clients.
- Keep utilities small; feature-specific business rules belong in services.
- Use import aliases such as `#services/` and `#models/` to keep cross-folder imports readable.
- Add only the files a feature needs, using consistent feature names across layers.

This layout makes it easy to find a particular kind of code while giving each layer a clear job.
