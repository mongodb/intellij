# MongoDB Plugin for IntelliJ

[![Quality Gate](https://github.com/mongodb/intellij/actions/workflows/pr-quality-gate.yaml/badge.svg)](https://github.com/mongodb/intellij/actions/workflows/pr-quality-gate.yaml)
[![GitHub Release](https://img.shields.io/github/v/release/mongodb-js/intellij?sort=semver&display_name=release&logo=github)](https://github.com/mongodb-js/intellij/releases)
[![IntelliJ Market Release](https://img.shields.io/github/v/release/mongodb-js/intellij?sort=semver&display_name=release&logo=jetbrains)](https://plugins.jetbrains.com/plugin/24377-mongodb)

The **MongoDB Plugin for IntelliJ** provides seamless integration with MongoDB deployments, whether they are local, containerized, or hosted on MongoDB Atlas (including those managed via `atlascli`). 
The plugin aims to enhance your development workflow with intelligent code autocompletion, valuable performance and correctness insights, and a dedicated query runner. 
It supports Java queries written with the [MongoDB Java Driver](https://www.mongodb.com/docs/drivers/java-drivers/) and [Spring Data MongoDB Criteria](https://spring.io/projects/spring-data-mongodb).

## Features

* **Effortless Connectivity:** Connect your IDE directly to any MongoDB cluster using your Data Explorer using a user-friendly side panel.
* **Intelligent Autocompletion:** Get smart suggestions for database names, collection names, and field names as you type.
* **Performance Insights:** Analyze your project for potential performance bottlenecks and receive actionable index suggestions.
* **Smart Type Checking:** Validate your schema and ensure the correctness of your queries with intelligent type checking.
* **Integrated Query Runner:** Transform your Java queries into valid `mongosh` queries and execute them directly within your IDE.

## Screenshots & Gifs

### Connecting to a Cluster
![Connecting to a Cluster](https://github.com/mongodb/intellij/tree/main/etc/readme/img/connecting.gif)

### Intelligent Autocompletion in Java
![Intelligent Autocompletion in Java](https://github.com/mongodb/intellij/tree/main/etc/readme/img/autocomplete.gif)

### Performance Insights & Create Index
![Performance Insights](https://github.com/mongodb/intellij/tree/main/etc/readme/img/create-index.gif)

### Run Java Queries
![Run Java Queries](https://github.com/mongodb/intellij/tree/main/etc/readme/img/run-java-queries.gif)

## Documentation
To get more information about plugin features and usage, please visit [the documentation](https://www.mongodb.com/docs/mongodb-intellij/install).

## Troubleshooting

You can report new bugs by creating a new issue either in [JIRA](https://jira.mongodb.org/projects/INTELLIJ/issues/) or
[GitHub](https://github.com/mongodbintellij/issues). Please include as much information as possible about your
environment and include any relevant logs.
