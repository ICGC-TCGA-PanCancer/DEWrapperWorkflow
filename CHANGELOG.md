# 1.0.5

## TODO

* need to use --uuid and other params to link two uploads to each other
* I think both the Delly and DKFZ docker containers will need to be revised to deal with multiple tumors
* create an uber-workflow
      * incorporate the Sanger and Broad workflows so all four run together
      * start with BWA workflow
      * will want to have a way to select which workflow to run, for the DKFZ workflow it will need to download Delly results if they are already submitted to GNOS
* on first run, the workflow pulls reference files from S3 and GNOS, we need a better alternative so that we do not eventually refer to broken links which would decrease the longevity of the workflow
* artifact for shared workflow modules that we can use for Sanger, DKFZ, EMBL, and maybe BWA workflows... specifically modules for upload download
* include bootstrap code to auto-provision work from central decider or /workflow.ini depending on ENV-Vars
* need to switch to --uuid for uploader so I know the output archive file name
* I may want to rethink the upload options so that you can upload to both S3 and GNOS at the same time, for example
* DKFZ is missing timing JSON file, I co-opted this for my full-workflow timing metrics but we really should get them to supply this and just add to it.  Also EMBL timing metrics need a total wall-time.
* print some helpful messages before the workflow runs indicating where the output will be, touch files for completion, what modes have been set, etc

# 1.0.4

* incrementing the version of the delly Docker image to 1.1, this image only changes the base Docker image used to build, it makes no scientific changes

# 1.0.3

* includes a bug fix for offline mode where XML file paths were not set correctly.

# 1.0.2

* re-orders upload to occur after both workflows are ready
* fixes default ini file paths
* fixes issue with pem key for gnos mounted in wrong location (fails workflows when running the first time without reference data handy)
* reverts the version change for dkfz which is hardcoded in Roddy (fails workflow across the board)

# 1.0.1

Version 1.0.1 represents non-scientific changes and mostly focus on the usability of local file mode, result archiving, and running workflows as non-root inside containers

* bug fixes to support creation of upload archive
* getting full offline mode working, ability to supply XML metadata files was the largest change
* run workflows as a non-root user
* docs updated

# 1.0.0

* Initial release
* Do not schedule 1.0.0 with multiple-tumor donors!!  1.0.1 will support this


