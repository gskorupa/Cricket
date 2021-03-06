# Cricket MSF microsite image
FROM openjdk:10-jre-slim

RUN mkdir /cricket
RUN mkdir /cricket/data
RUN mkdir /cricket/files
RUN mkdir /cricket/backup
COPY dist/cricket-1.4.4.jar /cricket
COPY dist/config/cricket.json /cricket/config/
COPY dist/www /cricket/www
COPY dist/data/cricket_publickeystore.jks /cricket/data
VOLUME /cricket
volume /cricket/www
WORKDIR /cricket

CMD ["java", "--illegal-access=deny", "--add-modules", "java.xml.bind", "--add-modules", "java.activation", "-jar", "cricket-1.4.4.jar", "-r", "-c", "/cricket/config/cricket.json", "-s", "Microsite"]