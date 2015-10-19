# The DKFZ/EMBL PanCancer Variant Calling Workflow

This project is intended to wrap the DKFZ and EMBL workflows as a single SeqWare workflow running inside it's own Docker container.  It is the orchestration workflow that calls GNOS download, the EMBL workflow for structural variation, the DKFZ workflow for SNVs, indels, and copy number, and finally upload of results back to GNOS.

Unlike previous workflows, there is now a central decider that generates INIs from a de-duplicated central index hosted on PanCancer.info.  This should be much more reliable than the distributed deciders used previously.  For more information see the [central-decider-client](https://github.com/ICGC-TCGA-PanCancer/central-decider-client).

For information on setting this workflow up in the context of a larger cloud of worker VMs provisioned and managed by a central launcher the doles out work from the INIs generated with the central decider, see our documentation [here](https://github.com/ICGC-TCGA-PanCancer/pancancer-documentation).

That infrastructure linked above is complex.  What follows are sufficient instructions for runing the workflow manually on a single box.  This is helpful for testing and/or small-scale runs.

[![Build Status](https://travis-ci.org/ICGC-TCGA-PanCancer/DEWrapperWorkflow.svg?branch=develop)](https://travis-ci.org/ICGC-TCGA-PanCancer/DEWrapperWorkflow)

## Contact

If you have questions please contact Brian O'Connor at boconnor@oicr.on.ca or the PCAWG shepherds list: PCAWG Shepherds <pcawg-shepherds@lists.icgc.org>

## For Users

The below section assumes that you are a user and primarily interested in running the workflow, not create a new version of it. It will walk you through running the workflow on a single Worker host (VM or regular Linux machine) and includes minimal instructions to build the workflow using (mostly) pre-created components.

### Docker Setup

In order to get this running, you will need to setup Docker on your worker host(s). It is recommended that you do this on an Amazon host with a 1024GB root disk (one good choice is ami-9a562df2, this should be an Ubuntu 14.04 image if you use another AMI). Alternatively, you can use a smaller root disk (say 100G) and then mount an encrypted 1024GB volume on /datastore so analysis is encrypted. We used a r3.8xlarge which has 32 cores and 256G of RAM which is probably too much. A min of 64G is recommended for this workflow so, ideally, you would have 32 cores and 64-128G or RAM.

Here is how you install Docker on Ubuntu 14.04, see the Docker [website](https://www.docker.com/) for more information:

        curl -sSL https://get.docker.com/ | sudo sh
        sudo usermod -aG docker ubuntu
        # log out then back in so changes take affect!
        exit

### Note About Workflow Versions

It's complicated.  The workflows have versions, the underlying tools have versions, and the Docker images built with these tools and workflows have versions.  Also this wrapper workflow itself has a version.  For the purposes of uploads we use this DEWrapper workflow version as the data upload version.  However keep in mind
 two things about the EMBL and DKFZ pipeline outputs respectively:

#### EMBL

The workflow is hosted on DockerHub and source in git.  Version 1.4.0 was tagged in git and is focused on multi-tumor support.  So the files produced are tagged with "embl-delly_1-3-0-preFilter".

#### DKFZ

The DKFZ system is hosted in github but can't be built without a controlled access Roddy binary. Version 1.3.0 was tagged in git and is focused on multi-tumor support.  The underlying version for Roddy is Roddy_2.2.49_COW_1.0.132-1_CNE_1.0.189.  The output files from this Docker image actually contain the strings "1-0-189" or "1-0-132-1".

### Docker Image Pull from DockerHub

Next, after logging back in, cache the seqware containers that we will be using

        docker pull pancancer/pcawg-dewrapper-workflow:1.0.7
        docker pull pancancer/pancancer_upload_download:1.2
        docker pull pancancer/pcawg-delly-workflow:1.4

### Docker Image Build for DKFZ

#### Option 1 - Download

Note, if you have been given a .tar of the DKFZ workflow you can skip the build below and just import it directly into Docker:

        docker load < dkfz_dockered_workflows_1.0.132-2.tar

Most people will not have this option since the tools are distributed as controlled access files.

#### Option 2 - Build

You need to get and build the DKFZ workflow since we are not allowed to redistribute it on DockerHub:

        git clone https://github.com/ICGC-TCGA-PanCancer/dkfz_dockered_workflows.git

See the [README](https://github.com/ICGC-TCGA-PanCancer/dkfz_dockered_workflows) for how to downloading Roddy bundles of data/binaries and build this Docker image.

        cd ~/gitroot/dkfz_dockered_workflows/
        # you need to download the Roddy binary, untar/gz, and move the Roddy directory into the current git checkout dir
        docker build -t pancancer/dkfz_dockered_workflows:1.3 .
        Successfully built 0805f987f138
        # you can list it out using...
        ubuntu@ip-10-169-171-198:~/gitroot/docker/dkfz_dockered_workflows$ docker images
        REPOSITORY                          TAG                 IMAGE ID            CREATED             VIRTUAL SIZE
        pancancer/dkfz_dockered_workflows   1.3                 0805f987f138        8 seconds ago       1.63 GB

Notice the tag here is set to 1.3 if the 1.3 tag from the DKFZ git repo is used to build this.  Update this value if building from a new tag.

### Directory Setup and Dependency Installs

Next, setup the shared directories on your worker host and get dependencies:

        sudo mkdir /workflows && sudo mkdir /datastore
        sudo chown ubuntu:ubuntu /workflows
        sudo chown ubuntu:ubuntu /datastore
        chmod a+wrx /workflows && chmod a+wrx /datastore
        wget https://seqwaremaven.oicr.on.ca/artifactory/seqware-release/com/github/seqware/seqware-distribution/1.1.1/seqware-distribution-1.1.1-full.jar
        sudo apt-get install openjdk-7-jdk maven

### GNOS Pem Key

Copy your pem key to:

        /home/ubuntu/.ssh/gnos.pem

### Run the Workflow in Test Mode

Now you can launch a test run of the workflow using the whitestar workflow engine which is much faster but lacks the more advanced features that are normally present in SeqWare. See [Developing in Partial SeqWare Environments with Whitestar](https://seqware.github.io/docs/6-pipeline/partial_environments/) for details.

This test run of the workflow is *not* fast. It is a real donor downloaded from GNOS and, therefore, is about a 48 hour run on a 32 core, 64G+ VM.  Do not try to run this on your laptop or a VM/host with limited resources.  Do not assume it uses fake test data, the test data is real, controlled access data.

The command to execute the workflow is:

      docker run --rm -h master -it -v /var/run/docker.sock:/var/run/docker.sock \
                                    -v /datastore:/datastore \
                                    -v /home/ubuntu/.ssh/gnos.pem:/home/ubuntu/.ssh/gnos.pem \
                  pancancer/pcawg-dewrapper-workflow:1.0.7 \
                  seqware bundle launch --dir /workflow/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 --engine whitestar --no-metadata

Look in your datastore for the `oozie-<uuid>` working directory created.  This contains the scripts/logs (generated-script directory) and the working directory for the two workflows (shared-data):

        ls -alhtr /datastore

### Launch Workflow with New INI File for Real Run

If you want to run with a specific INI:

        # edit the ini
        vim workflow.ini
        pancancer/pcawg-dewrapper-workflow:1.0.7
        docker run --rm -h master -it -v /var/run/docker.sock:/var/run/docker.sock \
                                      -v /datastore:/datastore \
                                      -v `pwd`/workflow.ini:/workflow.ini \
                                      -v /home/ubuntu/.ssh/gnos.pem:/home/ubuntu/.ssh/gnos.pem \
                    pancancer/pcawg-dewrapper-workflow:1.0.7 \
                      seqware bundle launch --dir /workflow/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 --engine whitestar --no-metadata --ini /workflow.ini

This is the approach you would take for running in production.  Each donor gets an INI file that is then used to launch a workflow using Docker.  If you choose to upload to S3 or GNOS your files should be uploaded there.  You can also find output in /datastore.

### Source of INIs

Adam Wright provides instructions [here](https://github.com/ICGC-TCGA-PanCancer/central-decider-client/blob/develop/README.md) on using a simple command line tool for generating INIs based on your site's allocation of donors.

You can use Adam's tool for generating many INI files, one per donor, and it takes care of choosing the correct input based on the curation work the OICR team has done. See the link above for more directions.

### User Tips for Workflow Settings

The INI files let you control many functions of the workflow.  Here are some of the most important and some that are
useful but difficult to understand.

#### Important Variables

Please note the following container versions. This mechanism is also how we will release new sub-components but generally these are fixed for a release of the DEWrapper workflow since the interface may change:

    dkfzDockerName=pancancer/dkfz_dockered_workflows
    emblDockerName=pancancer/pcawg-delly-workflow:1.4
    gnosDockerName=pancancer/pancancer_upload_download:1.2


#### Core variables that change per workflow

The INI contains several important variables that change from donor run to donor run.  These include:

        # General Donor Parameters
        donor_id=test_donor
        project_code=test_project
        # Inputs Parameters
        tumourAliquotIds=f393bb07-270c-2c93-e040-11ac0d484533
        tumourAnalysisIds=ef26d046-e88a-4f21-a232-16ccb43637f2
        tumourBams=7723a85b59ebce340fe43fc1df504b35.bam
        controlAnalysisId=1b9215ab-3634-4108-9db7-7e63139ef7e9
        controlBam=8f957ddae66343269cb9b854c02eee2f.bam
        # EMBL Parameters
        EMBL.delly_runID=f393bb07-270c-2c93-e040-11ac0d484533
        EMBL.input_bam_path_tumor=inputs/ef26d046-e88a-4f21-a232-16ccb43637f2
        EMBL.input_bam_path_germ=inputs/1b9215ab-3634-4108-9db7-7e63139ef7e9


#### File modes

There are three file modes for reading and writing: GNOS, local, S3.  Usually people use GNOS for both
reading and writing the files.  But local file mode can be used when files are already downloaded locally or
you want to asynchronously upload later so you just want to prepare an upload for later use.

These variables are set with:

        downloadSource=[GNOS,local,S3]
        uploadDestination=[GNOS,local,S3]

Keep in mind you can mix and match upload and download options here.  For example, you could download from GNOS
and then upload the resulting variant calls to S3.  Currently, you can't set a list of upload destinations so you
can only upload to one location per run of the workflow.

##### "local" file mode

        downloadSource=local
        uploadDestination=local

You can use local file mode for downloaded files. You need to use full paths to the BAM input files.

        tumourBams=<full_path>/7723a85b59ebce340fe43fc1df504b35.bam
        controlBam=<full_path>/8f957ddae66343269cb9b854c02eee2f.bam

The workflow will then symlink these files and continue the workflow.

For uploads, GNOS is still consulted for metadata unless the following parameter is included:

        localXMLMetadataPath=<path_to_directory_with_analysis_xml>

So this is a little complicated, when using local file upload mode (uploadDestination=local) *and* the previous variable is defined
the upload script will use XML files from this directory named data_<analysis_id>.xml.  It will also suppress the
validation and upload of metadata to GNOS from the upload tool.  In this way you can completely work offline.
By default it's simply not defined and, even in local file mode, GNOS will be queried for metadata when localXMLMetadataPath is null. If you
need to work fully offline make sure you pre-download the GNOS XML, put them in this directory, and name them
according to the standard mentioned above.

##### "GNOS" file mode

        downloadSource=GNOS
        uploadDestination=GNOS

This works similarly to other PanCancer workflows where input aligned BAM files are first downloaded from GNOS
and the resulting variant calls are uploaded to GNOS.  The download and upload servers may be different.  For this
option, you want to make sure you use just the BAM file name in the following variables and leave `localXMLMetadataPath`
blank:

        tumourBams=7723a85b59ebce340fe43fc1df504b35.bam
        controlBam=8f957ddae66343269cb9b854c02eee2f.bam

You also most have the GNOS servers for download and upload defined along with the PEM key files.  Note, in its current
form you can download all BAM inputs from one server and upload the results to one server.  Pulling from multiple
input servers is not yet possible.

        pemFile=/home/ubuntu/.ssh/gnos.pem
        gnosServer=https://gtrepo-ebi.annailabs.com
        uploadServer=https://gtrepo-ebi.annailabs.com
        uploadPemFile=/home/ubuntu/.ssh/gnos.pem

Obviously, the workflow host will need to be able to reach the GNOS servers multiple times in the workflow.

##### "S3" file mode

This is the least tested file mode.  The idea is that you can pre-stage data in S3 and then very quickly
pull inputs into an AWS host for processing.  At the end of the workflow you can then
write the submission tarball prepared for GNOS to S3 so you can latter upload to GNOS in batch.

To activate this mode:

        downloadSource=S3
        uploadDestination=S3

For downloads from S3:

        tumourBamS3Urls=s3://bucket/path/7723a85b59ebce340fe43fc1df504b35.bam
        controlBamS3Url=s3://bucket/path/8f957ddae66343269cb9b854c02eee2f.bam

You then refer to these using a non-full path:

        tumourBams=7723a85b59ebce340fe43fc1df504b35.bam
        controlBam=8f957ddae66343269cb9b854c02eee2f.bam

For uploads, you set the following for an upload path:

        uploadS3BucketPath=s3://bucket/path

And you also need to set your credentials used for both upload and download:

        s3Key=kljsdflkjsdlkfj
        s3SecretKey=lksdfjlsdkjflksdjfkljsd

Obviously, the workflow host will need to be able to reach AWS multiple times in the workflow so it's best to run
the full workflow in AWS if using this option.

#### upload archive tarball

For local file mode the VCF preparation process automatically creates a tarball bundle of the submission files
which is useful for archiving.  Currently, you don't have much control over where or how these tarballs are written,
you will find them in:

        uploadLocalPath=./upload_archive/

which is relative to the working directory's shared_workspace directory.  It's best to not change this because of the
nested nature of docker calling docker.  Instead, for local file mode, harvest the tarball archives from this directory.

The S3 upload mode also transfers the archive file to S3.

#### testing data

The workflow comes complete with details about a real donor in the EBI GNOS.  So this means you need to provide a
valid PEM key on the path specified:

        pemFile=/home/ubuntu/.ssh/gnos.pem
        uploadPemFile=/home/ubuntu/.ssh/gnos.pem

It's the same key and set to upload back to EBI under the test study.

        study-refname-override=icgc_pancancer_vcf_test

Keep in mind if you use two different keys (say you download and upload to different GNOS repos) then you need
to provide two `-v` options to the `docker run...` of this workflow, each pointing to a different pem path.

#### cleanup options

I recommend you cleanup bam files but not all.  That way your scripts and log files are preserved but you clean up most
space used by a workflow.  Once your system is working well, you should consider turning on the full cleanup, especially
if your worker nodes/VMs are long-lived.  In this case the variant call files left behind will cause the disk to fill up.

        cleanup=false
        cleanupBams=false

### Running With an alternate datastore path

There are three components to this currently, the first docker container is started with the following

    docker run --rm -h master -it -v /var/run/docker.sock:/var/run/docker.sock -v /not-datastore:/not-datastore  -v /workflows:/workflows -v `pwd`/different_dirs_workflow.ini:/workflow.ini -v /home/ubuntu/.ssh/gnos.pem:/home/ubuntu/.ssh/gnos.pem seqware/seqware_whitestar_pancancer:1.1.1  bash -c "sed -i 's/datastore/not-datastore/g' /home/seqware/.seqware/settings ; seqware bundle launch --dir /workflows/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 --engine whitestar --no-metadata --ini /workflow.ini"

First, for the section "-v /not-datastore:/not-datastore". Make sure that you match the path inside and outside the container. i.e. do not use something like "-v  /not-datastore:/datastore". If they do not match then the current workflow will fail, having created directories in the wrong locations.

Second, SeqWare needs to be informed to do its work in the new directory. That's the portion of the command "sed -i 's/datastore/not-datastore/g' /home/seqware/.seqware/settings". Otherwise, SeqWare will do its work in its default working directory of /datastore which is now solely within the container and will now disappear after the container stops.

Note that "/not-datastore" is arbitrary and can be changed on your system.

Third, "common\_data\_dir" is the ini parameter that needs to be changed to reflect the alternate path. The default ini file which lists all possible parameters is currently at https://github.com/ICGC-TCGA-PanCancer/DEWrapperWorkflow/blob/develop/workflow/config/DEWrapperWorkflow.ini#L44

#### Retry Options

The 1.1.1 version of SeqWare Whitestar includes global workflow-run retry functionality, per-step retry functionality, and a cleaner output which displays stderr and stdout on failure. See https://seqware.github.io/docs/6-pipeline/partial_environments/#additional-notes for details

In order to control the number of times SeqWare will retry workflow steps, you will need to modify the .seqware/settings keys defined in the above link. Please note that you can mount a customized .seqware/settings file using data volumes as in the example below:

    $ docker run --rm -h master -t -v `pwd`/datastore:/mnt/datastore -i seqware/seqware_whitestar cat ~/.seqware/settings > custom_settings
    $ vim custom_settings           (change whatever keys you want, including the default number of retries)
    $ docker run -v `pwd`/custom_settings:/home/seqware/.seqware/settings --rm -h master -t -v `pwd`/datastore:/mnt/datastore -i seqware/seqware_whitestar /bin/bash
    seqware@master:~$ grep RETRY ~/.seqware/settings
    OOZIE_RETRY_MAX=0
    OOZIE_RETRY_INTERVAL=5

In order to retry the workflow as a whole, since Whitestar has no database, it stores state information in the working directories for workflow runs. In order to retry in the simplest case:

    docker run --rm -h master -it -v /var/run/docker.sock:/var/run/docker.sock  -v /workflows:/workflows -v /datastore:/datastore  -v /home/ubuntu/.ssh/gnos.pem:/home/ubuntu/.ssh/gnos.pem seqware/seqware_whitestar_pancancer:1.1.1  bash -c "seqware workflow-run retry --working-dir /datastore/oozie-89d6858b-481f-4cef-bf51-0fe4824a8f03"

In order to retry with alternate datastore locations you'll need a command similar to the following:

    docker run --rm -h master -it -v /var/run/docker.sock:/var/run/docker.sock -v /not-datastore:/not-datastore  -v /workflows:/workflows -v /home/ubuntu/.ssh/gnos.pem:/home/ubuntu/.ssh/gnos.pem seqware/seqware_whitestar_pancancer:1.1.1  bash -c "sed -i 's/datastore/not-datastore/g' /home/seqware/.seqware/settings ; seqware workflow-run retry --working-dir /not-datastore/oozie-aad46ac7-60e7-46fb-9f95-f3552921734f"

### Scaling Up

As mentioned earlier, these direcions have instructed you on setting up a single VM to run the workflow on.  These instructions may be perfectly sufficient to scale up a fleet of worker nodes if you, say, have an HPC cluster, loop over your INI files, and schedule the `docker run` command as an HPC job.  For others working in clouds you may wish to investigate the use of our Arch 3.0 from PanCancer.  See [here](https://github.com/ICGC-TCGA-PanCancer/pancancer-documentation/blob/develop/production/setup_env.md#setting-up-a-pancancer-environment) for more details.

## For Developers

### DKFZ

Code is located at: https://github.com/SeqWare/docker/tree/develop/dkfz_dockered_workflows

You will need to build this one yourself since it cannot currently be distributed on DockerHub.

### EMBL

Original code is: https://bitbucket.org/weischen/pcawg-delly-workflow

Our import for build process is: https://github.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow

There is a SeqWare workflow and Docker image to go with it.  These are built by Travis and DockerHub respectively.

If there are changes on the original BitBucket repo they need to be mirrored to the GitHub repo to they are automatically built.


### Building DEWrapperWorkflow

If you wish to build DEWrapper from scratch, you can. Use these steps to build a copy of the workflow wrapping the DKFZ and EMBL pipelines:

        git clone git@github.com:ICGC-TCGA-PanCancer/DEWrapperWorkflow.git
        git checkout 1.0.7
        mvn clean install
        rsync -rauvL target/Workflow_Bundle_DEWrapperWorkflow_1.0.7_SeqWare_1.1.1 /workflows/


#### Github Bitbucket Sync

In order to keep the two of these up-to-date:

First, checkout from bitbucket:

    git clone <bitbucket embl repo>

Second, add github as a remote to your .gitconfig

    [remote "github"]
    url = git@github.com:ICGC-TCGA-PanCancer/pcawg_delly_workflow.git
    fetch = +refs/heads/*:refs/remotes/github/*

Third, pull from bitbucket and push to Github

    git pull origin master
    git push github

## Dependencies

This project uses components from the following projects

* [pcawg_embl_workflow](https://github.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow)
* [pcawg_dkfz_workflow](https://github.com/ICGC-TCGA-PanCancer/dkfz_dockered_workflows)
* [genetorrent](https://cghub.ucsc.edu/software/downloads.html)
