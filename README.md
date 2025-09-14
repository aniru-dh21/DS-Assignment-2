# Weather Aggregation System

## Project Purpose
This project implements a distributed weather aggregation system using Java Sockets and REST-like interface.

It consists of:
- **Aggregation Server** - receives weather data via `PUT`, serves data via `GET`, persists state to disk, and enforces Lamport clock ordering.
- **Content Server** - read weather files and periodically `PUT` them to the server.
- **GET Client** = fetch data via `GET` and display it.

The system ensures:
- Consistency with Lamport clocks
- Persistence (data survives crashes)
- Expiry (entries removed after 30s of inactivity)
- Concurrency control (multiple PUTs/GETs handled in Lamport order)

---

## Design Sketch

### Diagram

![Sketch](/Images/Design-Sketch.png)

### Components
- **Content Server** → sends weather updates (PUT) to Aggregation Server.  
- **Aggregation Server** → stores latest data, enforces Lamport ordering, persists to file.  
- **GET Client** → retrieves aggregated feed (GET) and displays results.  
- **Lamport Clock** → utility shared across all components for distributed time ordering.

## Setup Instructions

### Requirements
- Java 11+ (Java 17 recommended)
- Maven (for dependency management)
- IntelliJ IDEA (or any IDE, but tested on IntelliJ)

### Dependencies
Managed in `pom.xml`:
- `com.google.code.gson:gson` (JSON parsing)  
- `junit:junit` (testing)  

## Running the Application

### 1. Start Aggregation Server
Runs on port `4567` by default:
```bash
mvn compile
java -cp target/classes weather.server.AggregationServer 4567
```

### 2. Run a Content Server
Provide `host:port` and weather file path:
```bash
java -cp target/classes weather.content.ContentServer localhost:4567 data/weather.txt
```

Weather file format:
```
id:IDS60901
name:Adelaide
state:SA
air_temp:22.5
```

### 3. Run a GET Client
Fetch data from server:
```bash
java -cp target/classes weather.client.GETClient localhost:4567
```

---

### Running from IntelliJ
1. Open project in IntelliJ (`File -> Open -> pom.xml`).
2. Ensure Maven reimport has pulled dependencies (`gson`, `junit`).
3. Create Run Configurations:
   - `AggregationSever` -> Main class: `weather.server.AggregationServer`, Program args: `4567`
   - `ContentServer` -> Main class: `weather.content.ContentServer`, Program args: `localhost:4567 data/weather.txt`
   - `GETClient` -> Main class: `weather.client.GETClient`, Program args: `localhost:4567`
4. Run each configuration in order (Server -> Content -> Client).

## Running Tests
JUnit tests cover:
- Basic PUT/GET (`AggregationServerTest`)
- Expiry (30s) (`AggregationServerTest`)
- Persistence (`AggregationServerTest`)
- Concurrency / Lamport ordering (`ConcurrencyTest`)

Run via Maven:
```bash
mvn test
```

Or in IntelliJ
- Right-click `src/test/java` -> Run All Tests.

### Manual Stress Test
Run `StressTester` to simulate many ContentServers and GETClients:
```bash
java -cp target/classes weather.test.StressTester
```

## Notes
- Only GET and PUT methods are supported
- Responses:
  - `201 Created` -> First PUT for a new entry
  - `200 OK` -> Successful update
  - `204 No Content` -> Empty PUT body
  - `400 Bad Request` -> Unsupported request
  - `500 Internal Server Error` -> Invalid JSON
- Server persists state to `weather.json` and restores on restart
- Entries expire after 30 seconds of no updates.
