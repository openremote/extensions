# Decide which base image should be used
ARG QUALIFIER=latest

FROM openremote/manager:${QUALIFIER}-core

RUN mkdir -p /extensions
COPY extensions /extensions
