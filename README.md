# cxf-rt-example
An example application with Apache CXF Rest 3.x, Maven 3.x, Spring 4.x and Grails 2.5.x.

Prerequisites:

- Java 1.8
- Maven 3.2.1

How to build:

c:\projects\cxf-rt-example\
mvn clean install

Start CXF RT server:

cd c:\projects\cxf-rt-example\cxf-rt-server\
mvn jetty:run
open in the browser: http://localhost:8081/cxf-rt-server/

Start Grails frontend:

cd c:\projects\cxf-rt-example\grails-web\
mvn grails:run-app
open in the browser: http://localhost:8080/grails-web/
