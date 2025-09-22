# Open Data Connector

A collection of tools to facilitate access to Open Data sources, with a focus on Earth/environmental data from providers in EU/Germany.

## Setup

**Requirements**
- Java 17+
- Maven
- (optional) A valid Vaadin Pro licence for Charts
- (optional) A running instance of [Wetterdienst](https://github.com/earthobservations/wetterdienst) for DWD data retrieval and preprocessing

**Running**
```sh
# Build with Maven
$ mvn clean package

# Run the SpringBoot application
$ java -jar target/open-data-connector-<version>.jar
```


## Authors and acknowledgment
This project is based on code originally developed by [Tuan Anh](https://github.com/tuananh-aa-2001) at [ahu GmbH](https://www.ahu.de) within the scope of his Bachelor thesis in [Informatics at FH Aachen](https://www.fh-aachen.de/en/).

## License
Apache 2.0

