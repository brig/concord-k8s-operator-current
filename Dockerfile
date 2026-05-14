ARG from_image=gcr.io/distroless/java17-debian12
FROM $from_image

COPY target/operator.jar /operator.jar
CMD ["-Xmx128m", "-jar", "/operator.jar"]
