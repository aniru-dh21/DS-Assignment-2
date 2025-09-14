# Weather Aggregation System

## Project Purpose
This project implements a distributed weather aggregation system using Java Sockets and REST-like interface.

It consists of:
- **Aggregation Server** - receives weather data via `PUT`, serves data via `GET`, persists state to disk, and enforces Lamport clock ordering.
- **Content Server** - reads weather files and periodically `PUT` them to the server every 20 seconds.
- **GET Client** - fetches data via `GET` and displays it.

The system ensures:
- Consistency with Lamport clocks
- Persistence (data survives crashes)
- Expiry (entries removed after 30s of inactivity)
- Concurrency control (multiple PUTs/GETs handled safely)

---

## Design Sketch

### Diagram

![Sketch](/Images/Design-Sketch.png)

### Components
- **Content Server** → sends weather updates (PUT) to Aggregation Server every 20 seconds.  
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
java -cp target/classes Weather.server.AggregationServer 4567
```

### 2. Run a Content Server
Provide `host:port` and weather file path:
```bash
java -cp target/classes Weather.content.ContentServer localhost:4567 data/weather.txt
```

Weather file format (key:value pairs):
```
id:IDS60901
name:Adelaide
state:SA
air_temp:22.5
```

**Note**: Content Server automatically sends PUT requests every 20 seconds once started.

### 3. Run a GET Client
Fetch data from server:
```bash
java -cp target/classes Weather.client.GETClient localhost:4567
```

---

### Running from IntelliJ
1. Open project in IntelliJ (`File -> Open -> pom.xml`).
2. Ensure Maven reimport has pulled dependencies (`gson`, `junit`).
3. Create Run Configurations:
   - `AggregationServer` → Main class: `Weather.server.AggregationServer`, Program args: `4567`
   - `ContentServer` → Main class: `Weather.content.ContentServer`, Program args: `localhost:4567 data/weather.txt`
   - `GETClient` → Main class: `Weather.client.GETClient`, Program args: `localhost:4567`
4. Run each configuration in order (Server → Content → Client).

## Running Tests
JUnit tests cover:
- Basic PUT/GET functionality
- Expiry mechanism (30s timeout)
- Persistence and recovery
- Concurrency and Lamport ordering

Run via Maven:
```bash
mvn test
```

Or in IntelliJ:
- Right-click `src/test/java` → Run All Tests.

### Manual Stress Test
Run `StressTester` to simulate many ContentServers and GETClients:
```bash
java -cp target/classes Weather.test.StressTester
```

## System Behavior

### HTTP Methods Supported
- **GET /weather.json** - Returns all current weather data as JSON array
- **PUT /weather.json** - Accepts weather data in JSON format

### Response Codes
- `200 OK` → Successful GET or successful update of existing entry
- `201 Created` → First PUT for a new entry
- `204 No Content` → Empty PUT body
- `400 Bad Request` → Unsupported HTTP method
- `500 Internal Server Error` → Invalid JSON or processing error

### Persistence
- Server automatically persists state to `weather.json` file
- Data is restored on server restart
- Persistence occurs on every data change and expiry cleanup

### Data Expiry
- Entries expire after 30 seconds of no updates
- Expiry check runs every 5 seconds via ScheduledExecutorService
- Expired entries are automatically removed and persistence is updated

### Lamport Clock Synchronization
- All components maintain Lamport clocks for event ordering
- Clock values are exchanged via `Lamport-Clock` HTTP headers
- Ensures proper distributed synchronization across the system

### Concurrency
- Server handles multiple simultaneous client connections using threads
- Thread-safe data structures (ConcurrentHashMap) ensure data consistency
- Each client connection is processed in its own thread

## Package Structure
```
Weather/
├── client/
│   └── GETClient.java
├── content/
│   └── ContentServer.java
├── server/
│   └── AggregationServer.java
├── util/
    ├── LamportClock.java
    ├── PersistenceManager.java
    ├── HttpParser.java
    ├── HttpRequest.java
    └── HttpResponse.java
```

## Test Files and Cleanup
- Test files are automatically created and cleaned up
- JUnit tests use separate ports (5678, 6789) to avoid conflicts
- Temporary files: `station*.txt`, `weather.json` are auto-deleted after tests
- StressTester creates `station1.txt` through `station10.txt` for load testing

## Notes
- Content servers continuously run and send updates every 20 seconds
- The system is designed for high availability with automatic recovery
- File format parsing is flexible - any `key:value` format is supported
- Weather data must include an `id` field for proper aggregation
- Multiple content servers can run simultaneously with different data files
