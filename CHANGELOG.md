# 1.0.7

* incrementing the Delly Docker container from 1.3 to 1.4 to address multi-tumor file upload prep problem

# 1.0.6

* Fixed a bug which caused \*somatic.indel.vcf.gz.tbi to be added twice.
* Added a Docker file which can be used to build an image containing this workflow.

# 1.0.5

* the plotting code for the DKFZ container now uses <60G RAM (DKFZ 1.3 image)
* correctly combine the timing and QC json files, so it now supports the merged upload but also multi-tumor statistics collection
* combined upload, now using a single upload for DKFZ and EMBL sub-workflows, the official name is EMBLDKFZPancancerStrCnIndelSNV
* fixed a bug where upload server was used for the metadata URLs, actually needed to be download server

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


# TODO

* on first run, the workflow pulls reference files from S3 and GNOS, we need a better alternative so that we do not eventually refer to broken links which would decrease the longevity of the workflow
* artifact for shared workflow modules that we can use for Sanger, DKFZ, EMBL, and maybe BWA workflows... specifically modules for upload download
* need to switch to --uuid for uploader so I know the output archive file name
* I may want to rethink the upload options so that you can upload to both S3 and GNOS at the same time, for example
* DKFZ is missing timing JSON file, I co-opted this for my full-workflow timing metrics but we really should get them to supply this and just add to it.  Also EMBL timing metrics need a total wall-time.
* print some helpful messages before the workflow runs indicating where the output will be, touch files for completion, what modes have been set, etc
