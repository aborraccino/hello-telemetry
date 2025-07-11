# Use the official Tomcat image from the Docker Hub
#FROM tomcat:11.0
FROM artifactory.luigi.worldpay.io/docker/tomcat:11.0

# Copy your web application WAR file to the Tomcat webapps directory
COPY MyWebApp.war /usr/local/tomcat/webapps/

# Start Tomcat
CMD ["catalina.sh", "run"]
