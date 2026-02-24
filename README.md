# MII Process Report

In this repository you will find the process to send reports of current FHIR store implementation progress of DICs to a HRP.

## Documentation

The documentation of the process including the description, the deployment and configuration guides as well as instructions on how to start a process instance can be found in the [wiki](https://github.com/medizininformatik-initiative/mii-process-report/wiki).

## Development
Branching follows the git-flow model, for the latest development version see branch [develop](https://github.com/medizininformatik-initiative/mii-process-report/tree/develop).

## Build

Prerequisite: Java 17, Maven >= 3.6

To use this repository in your code, add the Github Package Registry server to your Maven `.m2/settings.xml`. Instructions on how to generate the `USERNAME` and `TOKEN` can be found in the GitHub documentation [Managing your personal access tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens). The token needs at least the `read:packages` scope.

```xml
<servers>
    <server>
        <id>github-mii</id>
        <username>USERNAME</username>
        <password>TOKEN</password>
    </server>
</servers>
```

## License
All code is published under the [Apache-2.0 License](LICENSE).

```sh
mvn net.revelc.code.formatter:formatter-maven-plugin:2.24.1:format net.revelc.code:impsort-maven-plugin:1.12.0:sort clean install -DskipTests=true
```
