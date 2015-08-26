FROM pancancer/seqware_whitestar_pancancer:1.1.1
MAINTAINER Solomon Shorser <solomon.shorser@oicr.on.ca>
LABEL workflow.name="DEWrapper" workflow.version="1.0.7"
# Run this container as:
# docker run --rm -h master -it \
#    -v /var/run/docker.sock:/var/run/docker.sock \
#    -v /datastore:/datastore \
#    -v /home/ubuntu/.ssh:/home/ubuntu/.ssh \
# pancancer/dewrapper:1.0.7 seqware bundle launch --dir /workflow/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 --engine whitestar --no-metadata

USER root
RUN apt-get -m update
RUN apt-get install -y apt-utils tar git curl nano wget dialog net-tools build-essential time
# Create a directory. This is where the workflow will be built.
RUN mkdir /code
# Copy over files needed to build the workflow.
COPY src /code/src
COPY pom.xml /code/pom.xml
COPY workflow.properties /code/workflow.properties
COPY links /code/links
COPY workflow /code/workflow
# Build the workflow.
WORKDIR /code
RUN mvn clean package
# Set up the built workflow in "/workflow"
RUN rsync -rauvL target/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 /workflow
WORKDIR /workflow
USER seqware
CMD ["/bin/bash"]
