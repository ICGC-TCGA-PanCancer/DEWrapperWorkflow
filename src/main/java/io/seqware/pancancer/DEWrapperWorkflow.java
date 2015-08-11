package io.seqware.pancancer;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map.Entry;
import net.sourceforge.seqware.pipeline.workflowV2.AbstractWorkflowDataModel;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;

/**
 * <p>
 * For more information on developing workflows, see the documentation at <a
 * href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare Java Workflows</a>.
 * </p>
 *
 * Quick reference for the order of methods called: 1. setupDirectory 2. setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 *
 * See the SeqWare API for <a href=
 * "http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29"
 * >AbstractWorkflowDataModel</a> for more information.
 */
public class DEWrapperWorkflow extends AbstractWorkflowDataModel {

    // constants
    public static final String S3 = "S3";
    public static final String GNOS = "GNOS";
    public static final String LOCAL = "local";

    // job utilities
    private final JobUtilities utils = new JobUtilities();

    // variables
    private static final String SHARED_WORKSPACE = "shared_workspace";
    private static final String UPLOAD_ARCHIVE_LOCATION = "upload_archive";
    public static final String SHARED_WORKSPACE_ABSOLUTE = "`pwd`/" + SHARED_WORKSPACE;
    private static final String DKFZ_RESULT_DIRECTORY_ABSOLUTE = SHARED_WORKSPACE_ABSOLUTE + "/results/";
    public static final String UPLOAD_ARCHIVE_IN_CONTAINER = "/datastore/" + UPLOAD_ARCHIVE_LOCATION;

    private static final String EMBL_PREFIX = "EMBL.";
    private static final String DKFZ_PREFIX = "DKFZ.";
    private static final String DKFZ_VERSION = Version.DKFZ_WORKFLOW_VERSION_UNDERSCORE;
    private List<String> analysisIds = null;
    private List<String> tumorAnalysisIds = null;
    private List<String> bams = null;
    private String gnosServer = null;
    private String pemFile = null;
    private String uploadServer = null;
    private String metadataURLs = null;
    private List<String> tumorAliquotIds = null;
    private String vmInstanceType;
    private String vmLocationCode;
    private String studyRefnameOverride = null;
    private String analysisCenterOverride = null;
    private String formattedDate;
    private String commonDataDir = "";
    private String dkfzDataBundleServer = "";
    private String dkfzDataBundleUUID = "";
    private String dkfzDataBundleFile = "";
    private String dkfzDataBundleDownloadKey = "";
    private String controlBam;
    private String controlAnalysisId;
    private String downloadSource = null;
    private String uploadDestination = null;
    // cleanupJob
    private Boolean cleanup = false;
    private Boolean cleanupBams = false;
    // GNOS timeout
    private int gnosTimeoutMin = 20;
    private int gnosRetries = 3;
    // S3
    private String controlS3URL = null;
    private List<String> allBamS3Urls = null;
    private String s3Key = null;
    private String s3SecretKey = null;
    private String uploadS3BucketPath = null;
    // docker names, do not specify defaults here. They are misleading and
    // they will be overridden by the embedded default ini anyways
    private String dkfzDockerName;
    private String emblDockerName;
    private String gnosDownloadName;
    private String localXMLMetadataPath;
    private List<String> localXMLMetadataFiles;

    @Override
    public void setupWorkflow() {
        try {

            // controls
            this.controlBam = getProperty("controlBam");
            this.controlAnalysisId = getProperty("controlAnalysisId");

            // these variables are for download of inputs
            this.analysisIds = Lists.newArrayList(getProperty("tumourAnalysisIds").split(","));
            analysisIds.add(controlAnalysisId);
            this.tumorAnalysisIds = Lists.newArrayList(getProperty("tumourAnalysisIds").split(","));
            this.bams = Lists.newArrayList(getProperty("tumourBams").split(","));
            bams.add(getProperty("controlBam"));
            this.gnosServer = getProperty("gnosServer");
            this.pemFile = getProperty("pemFile");

            // S3 URLs
            controlS3URL = getProperty("controlBamS3Url");
            allBamS3Urls = Lists.newArrayList(getProperty("tumourBamS3Urls").split(","));
            allBamS3Urls.add(controlS3URL);
            s3Key = getProperty("s3Key");
            s3SecretKey = getProperty("s3SecretKey");
            uploadS3BucketPath = getProperty("uploadS3BucketPath");

            // these variables are those extra required for EMBL upload
            this.uploadServer = getProperty("uploadServer");
            StringBuilder metadataURLBuilder = new StringBuilder();
            metadataURLBuilder.append(gnosServer).append("/cghub/metadata/analysisFull/").append(controlAnalysisId);
            for (String id : Lists.newArrayList(getProperty("tumourAnalysisIds").split(","))) {
                metadataURLBuilder.append(",").append(gnosServer).append("/cghub/metadata/analysisFull/").append(id);
            }
            this.metadataURLs = metadataURLBuilder.toString();
            this.tumorAliquotIds = Lists.newArrayList(getProperty("tumourAliquotIds").split(","));

            // background information on VMs
            // TODO: Cores and MemGb can be filled in at runtime
            this.vmInstanceType = getProperty("vmInstanceType");
            this.vmLocationCode = getProperty("vmLocationCode");

            // overrides for study name and analysis center
            if (this.hasPropertyAndNotNull("study-refname-override")) {
                this.studyRefnameOverride = getProperty("study-refname-override");
            }
            if (this.hasPropertyAndNotNull("analysis-center-override")) {
                this.analysisCenterOverride = getProperty("analysis-center-override");
            }

            // shared data directory
            commonDataDir = getProperty("common_data_dir");

            // DKFZ bundle info
            dkfzDataBundleServer = getProperty("DKFZ.dkfzDataBundleServer");
            dkfzDataBundleUUID = getProperty("DKFZ.dkfzDataBundleUUID");
            dkfzDataBundleFile = getProperty("DKFZ.dkfzDataBundleFile");
            dkfzDataBundleDownloadKey = getProperty("DKFZ.dkfzDataBundleDownloadKey");

            // record the date
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            Calendar cal = Calendar.getInstance();
            this.formattedDate = dateFormat.format(cal.getTime());

            // local file mode
            downloadSource = getProperty("downloadSource");
            uploadDestination = getProperty("uploadDestination");
            if (LOCAL.equals(downloadSource)) {
                System.err
                        .println("WARNING\n\tRunning in direct file mode, direct access BAM files will be used and assumed to be full paths\n");
            } else if (S3.equals(downloadSource)) {
                System.err
                        .println("WARNING\n\tRunning in S3 file mode, direct access BAM files will be used and assumed to be full paths\n");
            }
            if (LOCAL.equals(uploadDestination)) {
                System.err
                        .println("WARNING\n\tRunning in local file upload mode, analyzed results files will be written to a local directory, you will need to upload to GNOS yourself\n");
            } else if (S3.equals(uploadDestination)) {
                System.err
                        .println("WARNING\n\tRunning in S3 upload mode, analyzed results files will be written to an S3 bucket, you will need to upload to GNOS yourself\n");
            }

            if (hasPropertyAndNotNull("localXMLMetadataPath")) {
                localXMLMetadataPath = getProperty("localXMLMetadataPath");
                this.localXMLMetadataFiles = new ArrayList<>();
                // pre-construct local metadata paths as well
                localXMLMetadataFiles.add("data_" + controlAnalysisId + ".xml");
                for (String tumourAnalysisId : tumorAnalysisIds) {
                    localXMLMetadataFiles.add("data_" + tumourAnalysisId + ".xml");
                }
            }

            // timeout
            gnosTimeoutMin = Integer.parseInt(getProperty("gnosTimeoutMin"));
            gnosRetries = Integer.parseInt(getProperty("gnosRetries"));

            // cleanupJob
            if (hasPropertyAndNotNull("cleanup")) {
                cleanup = Boolean.valueOf(getProperty("cleanup"));
            }
            if (hasPropertyAndNotNull("cleanupBams")) {
                cleanupBams = Boolean.valueOf(getProperty("cleanupBams"));
            }

            /*
             * if(hasPropertyAndNotNull("runEmbl")) { runEmbl=Boolean.valueOf(getProperty("runEmbl")); }
             */

            // Docker images
            dkfzDockerName = getProperty("dkfzDockerName");
            emblDockerName = getProperty("emblDockerName");
            gnosDownloadName = getProperty("gnosDockerName");

        } catch (Exception e) {
            throw new RuntimeException("Could not read property from ini", e);
        }
    }

    /*
     * MAIN WORKFLOW METHOD
     */

    @Override
    /**
     * The core of the overall workflow
     */
    public void buildWorkflow() {

        // create a shared directory in /datastore on the host in order to download reference data
        Job createSharedWorkSpaceJob = createDirectoriesJob();

        // create reference EMBL data by calling download_data (currently a stub in the Perl version)
        Job getReferenceDataJob = createReferenceDataJob(createSharedWorkSpaceJob);

        // download DKFZ data from GNOS
        Job getDKFZReferenceDataJob = createDkfzReferenceDataJob(getReferenceDataJob);

        // create inputs
        Job lastDownloadDataJob = createDownloadDataJobs(getDKFZReferenceDataJob);

        // call the EMBL workflow
        Job emblJob = runEMBLWorkflow(lastDownloadDataJob);
        Job dkfzJob = null;

        // call the DKFZ workflow
        dkfzJob = runDKFZWorkflow(emblJob);

        // common upload job
        Job uploadJob = uploadJob();
        uploadJob.addParent(emblJob);
        uploadJob.addParent(dkfzJob);

        // now cleanupJob
        cleanupWorkflow(uploadJob);

    }

    /*
     * JOB BUILDING METHODS
     */

    private void cleanupWorkflow(Job... lastJobs) {
        Job cleanupJob = null;
        if (cleanup) {
            cleanupJob = this.getWorkflow().createBashJob("cleanup");
            cleanupJob.getCommand().addArgument("echo rf -Rf * \n");
        } else if (cleanupBams) {
            cleanupJob = this.getWorkflow().createBashJob("cleanupBams");
            cleanupJob.getCommand().addArgument("rm -f ./*/*.bam && ").addArgument("rm -f ./shared_workspace/*/*.bam; ");
        }
        for (Job lastJob : lastJobs) {
            if (lastJob != null && cleanupJob != null) {
                cleanupJob.addParent(lastJob);
            }
        }
    }

    /**
     *
     * @param previousJobPointer
     * @return a pointer to the last job created
     */
    private Job runEMBLWorkflow(Job previousJobPointer) {

        // call the EMBL workflow
        Job emblJob = this.getWorkflow().createBashJob("embl_workflow");

        // timing info
        emblJob.getCommand().addArgument("date +%s >> download_timing.txt \n");
        emblJob.getCommand().addArgument("date +%s > embl_timing.txt \n");

        // make config
        boolean count = true;
        for (Entry<String, String> entry : this.getConfigs().entrySet()) {
            if (entry.getKey().startsWith(EMBL_PREFIX)) {
                String cat = ">>";
                if (count) {
                    cat = ">";
                    count = false;
                }
                emblJob.getCommand().addArgument(
                // we need a better way of getting the ini file here, this may not be safe if the workflow has escaped key-values
                        "echo \"" + entry.getKey().replaceFirst(EMBL_PREFIX, "") + "\"=\"" + entry.getValue() + "\" " + cat + " "
                                + SHARED_WORKSPACE_ABSOLUTE + "/settings/embl.ini \n");
            }
        }
        // now supply date
        emblJob.getCommand().addArgument("echo \"date=" + formattedDate + "\" >> " + SHARED_WORKSPACE_ABSOLUTE + "/settings/embl.ini \n");

        // the actual docker command
        emblJob.getCommand()
                .addArgument(
                // this is the actual command we run inside the container, which is to launch a workflow
                        "docker run --rm -h master -v " + SHARED_WORKSPACE_ABSOLUTE
                                + ":/datastore "
                                // data files
                                + "-v "
                                + commonDataDir
                                + "/embl:/datafiles "
                                // mount the workflow.ini
                                + "-v "
                                + SHARED_WORKSPACE_ABSOLUTE
                                + "/settings/embl.ini:/workflow.ini "
                                // the container
                                + emblDockerName
                                + " "
                                // command received by seqware (replace this with a real call to Delly after getting bam files downloaded)
                                + "seqware bundle launch --dir /home/seqware/DELLY/target/Workflow_Bundle_DELLY_1.3.0_SeqWare_1.1.1 --engine whitestar-parallel --no-metadata --ini /workflow.ini\n");

        // timing
        emblJob.getCommand().addArgument("date +%s >> embl_timing.txt \n");

        emblJob.addParent(previousJobPointer);
        return emblJob;
    }

    private Job uploadJob() throws RuntimeException {

        // upload the EMBL and DKFZ results together

        List<String> vcfs = new ArrayList<>();
        List<String> tbis = new ArrayList<>();
        List<String> tars = new ArrayList<>();
        List<String> vcfmd5s = new ArrayList<>();
        List<String> tbimd5s = new ArrayList<>();
        List<String> tarmd5s = new ArrayList<>();
        List<String> qcFiles = new ArrayList<>();
        List<String> timingFiles = new ArrayList<>();

        // FIXME: really just need one timing file not broken down by tumorAliquotID! This will be key for multi-tumor donors
        String qcJson = null;
        String timingJson = null;

        // FIXME: these don't quite follow the naming convention
        for (String tumorAliquotId : tumorAliquotIds) {

            // DELLY FILES
            // String baseFile = "/workflow_data/" + tumorAliquotId + ".embl-delly_1-0-0-preFilter."+formattedDate;
            String baseFile = tumorAliquotId + "." + Version.EMBL_WORKFLOW_SHORT_NAME_VERSION + "." + formattedDate;

            qcJson = "`find . | grep " + baseFile + ".sv.qc.json | head -1`";
            timingJson = "`find . | grep " + baseFile + ".sv.timing.json | head -1`";

            // now add these to a list
            qcFiles.add("embl_qc_"+tumorAliquotId); qcFiles.add(qcJson);
            timingFiles.add("embl_timing_"+tumorAliquotId); timingFiles.add(timingJson);

            vcfs.add(baseFile + ".germline.sv.vcf.gz");
            vcfs.add(baseFile + ".sv.vcf.gz");
            vcfs.add(baseFile + ".somatic.sv.vcf.gz");

            vcfmd5s.add(baseFile + ".germline.sv.vcf.gz.md5");
            vcfmd5s.add(baseFile + ".sv.vcf.gz.md5");
            vcfmd5s.add(baseFile + ".somatic.sv.vcf.gz.md5");

            tbis.add(baseFile + ".germline.sv.vcf.gz.tbi");
            tbis.add(baseFile + ".sv.vcf.gz.tbi");
            tbis.add(baseFile + ".somatic.sv.vcf.gz.tbi");

            tbimd5s.add(baseFile + ".germline.sv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + ".sv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + ".somatic.sv.vcf.gz.tbi.md5");

            tars.add(baseFile + ".germline.sv.readname.txt.tar.gz");
            tarmd5s.add(baseFile + ".germline.sv.readname.txt.tar.gz.md5");

            tars.add(baseFile + ".germline.sv.bedpe.txt.tar.gz");
            tarmd5s.add(baseFile + ".germline.sv.bedpe.txt.tar.gz.md5");

            tars.add(baseFile + ".somatic.sv.readname.txt.tar.gz");
            tarmd5s.add(baseFile + ".somatic.sv.readname.txt.tar.gz.md5");

            tars.add(baseFile + ".somatic.sv.bedpe.txt.tar.gz");
            tarmd5s.add(baseFile + ".somatic.sv.bedpe.txt.tar.gz.md5");

            tars.add(baseFile + ".sv.cov.plots.tar.gz");
            tarmd5s.add(baseFile + ".sv.cov.plots.tar.gz.md5");

            tars.add(baseFile + ".sv.cov.tar.gz");
            tarmd5s.add(baseFile + ".sv.cov.tar.gz.md5");

            // this is a new file
            tars.add(baseFile + ".sv.log.tar.gz");
            tarmd5s.add(baseFile + ".sv.log.tar.gz.md5");


            // DKFZ FILES
            // String baseFile = "/workflow_data/" + tumorAliquotId + ".dkfz-";

            baseFile = tumorAliquotId + ".dkfz-";

            timingJson = "./shared_workspace/results/timing.json";

            // now add these to a list
            qcFiles.add("dkfz_qc_indel_"+tumorAliquotId); qcFiles.add(DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/" + baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".indel.json");
            qcFiles.add("dkfz_qc_snv_mnv_"+tumorAliquotId); qcFiles.add(DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/" + baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".snv_mnv.json");
            qcFiles.add("dkfz_qc_cnv_"+tumorAliquotId); qcFiles.add(DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/" + baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".cnv.gcbias.json");
            timingFiles.add("global_timing_"+tumorAliquotId); timingFiles.add(timingJson);

            // VCF
            vcfs.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.indel.vcf.gz");
            vcfs.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.vcf.gz");
            vcfs.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.snv_mnv.vcf.gz");
            vcfs.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.vcf.gz");
            vcfs.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.vcf.gz");

            // VCF MD5
            vcfmd5s.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.indel.vcf.gz.md5");
            vcfmd5s.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.vcf.gz.md5");
            vcfmd5s.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.snv_mnv.vcf.gz.md5");
            vcfmd5s.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.md5");
            vcfmd5s.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.vcf.gz.md5");

            // Tabix
            tbis.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.indel.vcf.gz.tbi");
            tbis.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.vcf.gz.tbi");
            tbis.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.snv_mnv.vcf.gz.tbi");
            tbis.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.tbi");
            tbis.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.vcf.gz.tbi");

            // Tabix MD5
            tbimd5s.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.indel.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".germline.snv_mnv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.vcf.gz.tbi.md5");

            // Tarballs
            tars.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.tar.gz");
            tars.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.tar.gz");
            tars.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.tar.gz");

            // Tarballs MD5
            tarmd5s.add(baseFile + "copyNumberEstimation_" + Version.DKFZ_CNV_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.cnv.tar.gz.md5");
            tarmd5s.add(baseFile + "indelCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.indel.tar.gz.md5");
            tarmd5s.add(baseFile + "snvCalling_" + Version.DKFZ_SNV_INDEL_WORKFLOW_VERSION_UNDERSCORE + "." + formattedDate + ".somatic.snv_mnv.tar.gz.md5");

        }
        // perform upload to GNOS
        // FIXME: hardcoded versions, URLs, etc
        Job uploadJob = this.getWorkflow().createBashJob("upload");

        // cleanup JSON to make single line, combine JSON
        String summaryQcJSON = "summary_qc.json";
        uploadJob.getCommand().addArgument("perl " + this.getWorkflowBaseDir() + "/scripts/prep_json.pl "+Joiner.on(" ").join(qcFiles) +" > " + DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/" + summaryQcJSON + "\n");
        String summaryTimingJSON = "summary_timing.json";
        uploadJob.getCommand().addArgument("perl " + this.getWorkflowBaseDir() + "/scripts/prep_json.pl "+Joiner.on(" ").join(timingFiles) +" > " + DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/" + summaryTimingJSON + "\n");

        // copy the Delly results into the results folder to mix with DKFZ
        uploadJob.getCommand().addArgument("cp "+SHARED_WORKSPACE_ABSOLUTE+"/*." + Version.EMBL_WORKFLOW_SHORT_NAME_VERSION + "." + formattedDate + "* " + DKFZ_RESULT_DIRECTORY_ABSOLUTE + "/\n");

        // params
        StringBuilder overrideTxt = new StringBuilder();
        if (this.studyRefnameOverride != null) {
            overrideTxt.append(" --study-refname-override ").append(this.studyRefnameOverride);
        }
        if (this.analysisCenterOverride != null) {
            overrideTxt.append(" --analysis-center-override ").append(this.analysisCenterOverride);
        }
        // Now do the upload based on the destination chosen
        // NOTE: I'm using the wrapper workflow version here so it's immediately obvious what wrapper was used
        if (LOCAL.equalsIgnoreCase(uploadDestination)) {

            // using hard links so it spans multiple exported filesystems to Docker
            uploadJob = utils.localUploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, gnosTimeoutMin, gnosRetries, summaryQcJSON, summaryTimingJSON, Version.WORKFLOW_SRC_URL,
                    Version.WORKFLOW_URL, Version.WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName,
                    this.localXMLMetadataPath, this.localXMLMetadataFiles);

        } else if (GNOS.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.gnosUploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    gnosTimeoutMin, gnosRetries, summaryQcJSON, summaryTimingJSON, Version.WORKFLOW_SRC_URL,
                    Version.WORKFLOW_URL, Version.WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName);

        } else if (S3.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.s3UploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s, tars,
                    tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, s3Key, s3SecretKey, uploadS3BucketPath, gnosTimeoutMin, gnosRetries, summaryQcJSON, summaryTimingJSON,
                    Version.WORKFLOW_SRC_URL, Version.WORKFLOW_URL, Version.WORKFLOW_NAME,  Version.WORKFLOW_VERSION,
                    gnosDownloadName);

        } else {
            throw new RuntimeException("Don't know what upload type '" + uploadDestination + "' is!");
        }
        return uploadJob;
    }

    private Job uploadEMBLJob() throws RuntimeException {
        // upload the EMBL results

        List<String> vcfs = new ArrayList<>();
        List<String> tbis = new ArrayList<>();
        List<String> tars = new ArrayList<>();
        List<String> vcfmd5s = new ArrayList<>();
        List<String> tbimd5s = new ArrayList<>();
        List<String> tarmd5s = new ArrayList<>();
        // FIXME: really just need one timing file not broken down by tumorAliquotID! This will be key for multi-tumor donors
        String qcJson = null;
        String timingJson = null;
        // FIXME: these don't quite follow the naming convention
        for (String tumorAliquotId : tumorAliquotIds) {

            // String baseFile = "/workflow_data/" + tumorAliquotId + ".embl-delly_1-0-0-preFilter."+formattedDate;
            String baseFile = tumorAliquotId + ".embl-delly_1-3-0-preFilter." + formattedDate;

            qcJson = "`find . | grep " + baseFile + ".sv.qc.json | head -1`";
            timingJson = "`find . | grep " + baseFile + ".sv.timing.json | head -1`";

            vcfs.add(baseFile + ".germline.sv.vcf.gz");
            vcfs.add(baseFile + ".sv.vcf.gz");
            vcfs.add(baseFile + ".somatic.sv.vcf.gz");

            vcfmd5s.add(baseFile + ".germline.sv.vcf.gz.md5");
            vcfmd5s.add(baseFile + ".sv.vcf.gz.md5");
            vcfmd5s.add(baseFile + ".somatic.sv.vcf.gz.md5");

            tbis.add(baseFile + ".germline.sv.vcf.gz.tbi");
            tbis.add(baseFile + ".sv.vcf.gz.tbi");
            tbis.add(baseFile + ".somatic.sv.vcf.gz.tbi");

            tbimd5s.add(baseFile + ".germline.sv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + ".sv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + ".somatic.sv.vcf.gz.tbi.md5");

            tars.add(baseFile + ".germline.sv.readname.txt.tar.gz");
            tarmd5s.add(baseFile + ".germline.sv.readname.txt.tar.gz.md5");

            tars.add(baseFile + ".germline.sv.bedpe.txt.tar.gz");
            tarmd5s.add(baseFile + ".germline.sv.bedpe.txt.tar.gz.md5");

            tars.add(baseFile + ".somatic.sv.readname.txt.tar.gz");
            tarmd5s.add(baseFile + ".somatic.sv.readname.txt.tar.gz.md5");

            tars.add(baseFile + ".somatic.sv.bedpe.txt.tar.gz");
            tarmd5s.add(baseFile + ".somatic.sv.bedpe.txt.tar.gz.md5");

            tars.add(baseFile + ".sv.cov.plots.tar.gz");
            tarmd5s.add(baseFile + ".sv.cov.plots.tar.gz.md5");

            tars.add(baseFile + ".sv.cov.tar.gz");
            tarmd5s.add(baseFile + ".sv.cov.tar.gz.md5");

        }
        // perform upload to GNOS
        // FIXME: hardcoded versions, URLs, etc
        Job uploadJob = this.getWorkflow().createBashJob("uploadEMBL");
        // params
        StringBuilder overrideTxt = new StringBuilder();
        if (this.studyRefnameOverride != null) {
            overrideTxt.append(" --study-refname-override ").append(this.studyRefnameOverride);
        }
        if (this.analysisCenterOverride != null) {
            overrideTxt.append(" --analysis-center-override ").append(this.analysisCenterOverride);
        }
        // Now do the upload based on the destination chosen
        // NOTE: I'm using the wrapper workflow version here so it's immediately obvious what wrapper was used
        if (LOCAL.equalsIgnoreCase(uploadDestination)) {

            // using hard links so it spans multiple exported filesystems to Docker
            uploadJob = utils.localUploadJob(uploadJob, SHARED_WORKSPACE_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, gnosTimeoutMin, gnosRetries, qcJson, timingJson, Version.EMBL_WORKFLOW_SRC_URL,
                    Version.EMBL_WORKFLOW_URL, Version.EMBL_WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName,
                    this.localXMLMetadataPath, this.localXMLMetadataFiles);

        } else if (GNOS.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.gnosUploadJob(uploadJob, SHARED_WORKSPACE_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    gnosTimeoutMin, gnosRetries, qcJson, timingJson, Version.EMBL_WORKFLOW_SRC_URL, Version.EMBL_WORKFLOW_URL,
                    Version.EMBL_WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName);

        } else if (S3.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.s3UploadJob(uploadJob, SHARED_WORKSPACE_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s, tars,
                    tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, s3Key, s3SecretKey, uploadS3BucketPath, gnosTimeoutMin, gnosRetries, qcJson, timingJson,
                    Version.EMBL_WORKFLOW_SRC_URL, Version.EMBL_WORKFLOW_URL, Version.EMBL_WORKFLOW_NAME, Version.WORKFLOW_VERSION,
                    gnosDownloadName);

        } else {
            throw new RuntimeException("Don't know what download Type " + downloadSource + " is!");
        }
        return uploadJob;
    }

    private Job runDKFZWorkflow(Job previousJobPointer) {

        // generate the tumor array
        List<String> tumorBams = new ArrayList<>();
        for (int i = 0; i < tumorAnalysisIds.size(); i++) {
            if (LOCAL.equals(downloadSource)) {
                String[] tokens = bams.get(i).split("/");
                String bamFile = tokens[tokens.length - 1];
                tumorBams.add("/mnt/datastore/workflow_data/inputdata/" + tumorAnalysisIds.get(i) + "/" + bamFile);
            } else {
                tumorBams.add("/mnt/datastore/workflow_data/inputdata/" + tumorAnalysisIds.get(i) + "/" + bams.get(i));
            }
        }

        // generate control bam
        if (LOCAL.equals(downloadSource)) {
            String[] tokens = controlBam.split("/");
            controlBam = tokens[tokens.length - 1];
        }

        // tumor delly files
        List<String> tumorDelly = new ArrayList<>();
        for (String tumorAliquotId : tumorAliquotIds) {
            tumorDelly.add("/mnt/datastore/workflow_data/inputdata/" + tumorAliquotId + "." + Version.EMBL_WORKFLOW_SHORT_NAME_VERSION + "." + formattedDate
                    + ".somatic.sv.bedpe.txt");
        }

        Job generateIni = this.getWorkflow().createBashJob("generateDKFZ_ini");
        generateIni.getCommand().addArgument(
                "echo \"#!/bin/bash\n" + "tumorBams=( " + Joiner.on(" ").join(tumorBams) + " )\n" + "aliquotIDs=( "
                        + Joiner.on(" ").join(tumorAliquotIds) + " )\n" + "controlBam=/mnt/datastore/workflow_data/inputdata/"
                        + controlAnalysisId + "/" + controlBam + "\n" + "dellyFiles=( " + Joiner.on(" ").join(tumorDelly) + " )\n"
                        + "runACEeq=true\n" + "runSNVCalling=true\n" + "runIndelCalling=true\n" + "date=" + this.formattedDate + "\" > "
                        + SHARED_WORKSPACE + "/settings/dkfz.ini \n");
        generateIni.addParent(previousJobPointer);

        // prepare file mount paths
        StringBuffer mounts = new StringBuffer();
        for (int i = 0; i < tumorAliquotIds.size(); i++) {
            String aliquotId = tumorAliquotIds.get(i);
            String analysisId = tumorAnalysisIds.get(i);
            mounts.append(" -v " + SHARED_WORKSPACE_ABSOLUTE + "/inputs/").append(analysisId)
                    .append(":/mnt/datastore/workflow_data/inputdata/").append(analysisId).append(" ");
            mounts.append(" -v " + SHARED_WORKSPACE_ABSOLUTE + "/").append(aliquotId).append("."+Version.EMBL_WORKFLOW_SHORT_NAME_VERSION+".")
                    .append(formattedDate).append(".somatic.sv.bedpe.txt:/mnt/datastore/workflow_data/inputdata/").append(aliquotId)
                    .append("." + Version.EMBL_WORKFLOW_SHORT_NAME_VERSION + ".").append(formattedDate).append(".somatic.sv.bedpe.txt ");
        }
        // now deal with the control
        mounts.append(" -v " + SHARED_WORKSPACE_ABSOLUTE + "/inputs/").append(controlAnalysisId)
                .append(":/mnt/datastore/workflow_data/inputdata/").append(controlAnalysisId).append(" ");

        // run the docker for DKFZ
        Job runWorkflow = this.getWorkflow().createBashJob("runDKFZ");
        runWorkflow.getCommand().addArgument("date +%s > dkfz_timing.txt \n");
        runWorkflow.getCommand().addArgument(
                "docker run "
                        // container seems to assume that the host is called master
                        + "-h master "
                        // mount shared directories
                        + "-v " + commonDataDir + "/dkfz/" + dkfzDataBundleUUID
                        + "/bundledFiles:/mnt/datastore/bundledFiles "
                        // this path does not look right
                        + mounts + "-v " + SHARED_WORKSPACE_ABSOLUTE + "/testdata:/mnt/datastore/testdata " + "-v "
                        + SHARED_WORKSPACE_ABSOLUTE + "/settings/dkfz.ini:/mnt/datastore/workflow_data/workflow.ini " + "-v "
                        + SHARED_WORKSPACE_ABSOLUTE + "/results:/mnt/datastore/resultdata "
                        // the DKFZ image and the command we feed into it follow
                        + dkfzDockerName + " /bin/bash -c '/roddy/bin/runwrapper.sh' \n");
        runWorkflow.getCommand().addArgument("date +%s >> dkfz_timing.txt \n");

        // summarize timing info since DKFZ does not provide a timing.json
        runWorkflow.getCommand().addArgument(
                "perl " + this.getWorkflowBaseDir() + "/scripts/timing.pl > " + SHARED_WORKSPACE_ABSOLUTE + "/results/timing.json");

        runWorkflow.addParent(generateIni);

        return runWorkflow;
    }

    private Job uploadDKFZJob() throws RuntimeException {
        // upload the DKFZ results

        List<String> vcfs = new ArrayList<>();
        List<String> tbis = new ArrayList<>();
        List<String> tars = new ArrayList<>();
        List<String> vcfmd5s = new ArrayList<>();
        List<String> tbimd5s = new ArrayList<>();
        List<String> tarmd5s = new ArrayList<>();

        // FIXME: really just need one timing file not broken down by tumorAliquotID! This will be key for multi-tumor donors
        String qcJson = null;
        String qcJsonSingle = null;
        String timingJson = null;

        for (String tumorAliquotId : tumorAliquotIds) {

            // String baseFile = "/workflow_data/" + tumorAliquotId + ".dkfz-";
            String baseFile = tumorAliquotId + ".dkfz-";

            qcJson = tumorAliquotId + ".qc_metrics.dkfz.json";
            qcJsonSingle = tumorAliquotId + ".qc_metrics.dkfz.single.json";
            timingJson = "timing.json";

            // VCF
            vcfs.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.indel.vcf.gz");
            vcfs.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz");
            vcfs.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.snv_mnv.vcf.gz");
            vcfs.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.vcf.gz");
            vcfs.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.vcf.gz");

            // VCF MD5
            vcfmd5s.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.indel.vcf.gz.md5");
            vcfmd5s.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz.md5");
            vcfmd5s.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.snv_mnv.vcf.gz.md5");
            vcfmd5s.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.md5");
            vcfmd5s.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.vcf.gz.md5");

            // Tabix
            tbis.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz.tbi");
            tbis.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz.tbi");
            tbis.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.snv_mnv.vcf.gz.tbi");
            tbis.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.tbi");
            tbis.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.vcf.gz.tbi");

            // Tabix MD5
            tbimd5s.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".germline.snv_mnv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.vcf.gz.tbi.md5");
            tbimd5s.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.vcf.gz.tbi.md5");

            // Tarballs
            tars.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.tar.gz");
            tars.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.tar.gz");
            tars.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.tar.gz");

            // Tarballs MD5
            tarmd5s.add(baseFile + "copyNumberEstimation_" + DKFZ_VERSION + "." + formattedDate + ".somatic.cnv.tar.gz.md5");
            tarmd5s.add(baseFile + "indelCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.indel.tar.gz.md5");
            tarmd5s.add(baseFile + "snvCalling_" + DKFZ_VERSION + "." + formattedDate + ".somatic.snv_mnv.tar.gz.md5");

        }

        Job uploadJob = this.getWorkflow().createBashJob("uploadDKFZ");

        // have to do this to cleanupJob the multi-line JSON
        uploadJob.getCommand().addArgument(
                "cat " + SHARED_WORKSPACE + "/results/" + qcJson + " | perl -p -e 's/\\n/ /g' > " + SHARED_WORKSPACE + "/results/"
                        + qcJsonSingle + " \n");

        StringBuilder overrideTxt = new StringBuilder();
        if (this.studyRefnameOverride != null) {
            overrideTxt.append(" --study-refname-override ").append(this.studyRefnameOverride);
        }
        if (this.analysisCenterOverride != null) {
            overrideTxt.append(" --analysis-center-override ").append(this.analysisCenterOverride);
        }

        // Now do the upload based on the destination chosen
        // NOTE: I'm using the wrapper workflow version here so it's immediately obvious what wrapper was used
        if (LOCAL.equalsIgnoreCase(uploadDestination)) {

            // using hard links so it spans multiple exported filesystems to Docker
            uploadJob = utils.localUploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis,
                    tbimd5s, tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, gnosTimeoutMin, gnosRetries, qcJsonSingle, timingJson, Version.DKFZ_WORKFLOW_SRC_URL,
                    Version.DKFZ_WORKFLOW_URL, Version.DKFZ_WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName,
                    this.localXMLMetadataPath, this.localXMLMetadataFiles);

        } else if (GNOS.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.gnosUploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    gnosTimeoutMin, gnosRetries, qcJsonSingle, timingJson, Version.DKFZ_WORKFLOW_SRC_URL, Version.DKFZ_WORKFLOW_URL,
                    Version.DKFZ_WORKFLOW_NAME, Version.WORKFLOW_VERSION, gnosDownloadName);

        } else if (S3.equalsIgnoreCase(uploadDestination)) {

            uploadJob = utils.s3UploadJob(uploadJob, DKFZ_RESULT_DIRECTORY_ABSOLUTE, pemFile, metadataURLs, vcfs, vcfmd5s, tbis, tbimd5s,
                    tars, tarmd5s, uploadServer, Version.SEQWARE_VERSION, vmInstanceType, vmLocationCode, overrideTxt.toString(),
                    UPLOAD_ARCHIVE_IN_CONTAINER, s3Key, s3SecretKey, uploadS3BucketPath, gnosTimeoutMin, gnosRetries, qcJsonSingle,
                    timingJson, Version.DKFZ_WORKFLOW_SRC_URL, Version.DKFZ_WORKFLOW_URL, Version.DKFZ_WORKFLOW_NAME,
                    Version.WORKFLOW_VERSION, gnosDownloadName);

        } else {
            throw new RuntimeException("Don't know what download Type " + downloadSource + " is!");
        }
        return uploadJob;
    }

    private Job createDirectoriesJob() {
        Job createSharedWorkSpaceJob = this.getWorkflow().createBashJob("create_dirs");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + " \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/settings \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/results \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/working \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/downloads/dkfz \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/downloads/embl \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/inputs \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/testdata \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/uploads \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + SHARED_WORKSPACE + "/data \n"); // deprecated, using data
                                                                                                                // dirs below
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + commonDataDir + "/dkfz \n");
        createSharedWorkSpaceJob.getCommand().addArgument("mkdir -m 0777 -p " + commonDataDir + "/embl \n");

        return createSharedWorkSpaceJob;
    }

    private Job createReferenceDataJob(Job createSharedWorkSpaceJob) {

        Job getReferenceDataJob = this.getWorkflow().createBashJob("getEMBLDataFiles");
        getReferenceDataJob.getCommand().addArgument("date +%s > reference_timing.txt \n");
        getReferenceDataJob.getCommand().addArgument("cd " + commonDataDir + "/embl \n");
        getReferenceDataJob
                .getCommand()
                .addArgument(
                        "if [ ! -f genome.fa ]; then wget http://s3.amazonaws.com/pan-cancer-data/pan-cancer-reference/genome.fa.gz \n gunzip genome.fa.gz || true \n fi \n");
        // upload this to S3 after testing
        getReferenceDataJob
                .getCommand()
                .addArgument(
                        "if [ ! -f hs37d5_1000GP.gc ]; then wget https://s3.amazonaws.com/pan-cancer-data/pan-cancer-reference/hs37d5_1000GP.gc \n fi \n");
        getReferenceDataJob.getCommand().addArgument("cd - \n");
        getReferenceDataJob.getCommand().addArgument("date +%s >> reference_timing.txt \n");
        getReferenceDataJob.addParent(createSharedWorkSpaceJob);
        return getReferenceDataJob;

    }

    private Job createDkfzReferenceDataJob(Job getReferenceDataJob) {
        Job getDKFZReferenceDataJob = this.getWorkflow().createBashJob("getDKFZDataFiles");
        getDKFZReferenceDataJob.getCommand().addArgument("date +%s > dkfz_reference_timing.txt \n");
        getDKFZReferenceDataJob.getCommand().addArgument("cd " + commonDataDir + "/dkfz \n");
        getDKFZReferenceDataJob
                .getCommand()
                .addArgument(
                        "if [ ! -d "
                                + dkfzDataBundleUUID
                                + "/bundledFiles ]; then docker run "
                                // link in the input directory
                                + "-v `pwd`:/workflow_data "
                                // link in the pem key
                                + "-v "
                                + dkfzDataBundleDownloadKey
                                + ":/gnos_icgc_keyfile.pem "
                                + gnosDownloadName
                                // here is the Bash command to be run
                                + " /bin/bash -c 'cd /workflow_data/ && perl -I /opt/gt-download-upload-wrapper/gt-download-upload-wrapper-2.0.11/lib "
                                + "/opt/vcf-uploader/vcf-uploader-2.0.5/gnos_download_file.pl " + "--url " + dkfzDataBundleServer
                                + "/cghub/data/analysis/download/" + dkfzDataBundleUUID + " --file " + dkfzDataBundleUUID + "/"
                                + dkfzDataBundleFile + " --retries " + gnosRetries + " --timeout-min " + gnosTimeoutMin + " "
                                + "  --pem /gnos_icgc_keyfile.pem && " + "cd " + dkfzDataBundleUUID + " && " + "tar zxf "
                                + dkfzDataBundleFile + "' \n fi \n ");
        getDKFZReferenceDataJob.getCommand().addArgument("cd - \n");
        getDKFZReferenceDataJob.getCommand().addArgument("date +%s >> dkfz_reference_timing.txt \n");
        getDKFZReferenceDataJob.getCommand().addArgument("date +%s > download_timing.txt \n");
        getDKFZReferenceDataJob.addParent(getReferenceDataJob);
        return getDKFZReferenceDataJob;
    }

    private Job createDownloadDataJobs(Job previousJob) {

        Job previousJobPointer = previousJob;

        for (int i = 0; i < analysisIds.size(); i++) {

            Job downloadJob = this.getWorkflow().createBashJob("download_" + i);

            if (LOCAL.equalsIgnoreCase(downloadSource)) {

                // using hard links so it spans multiple exported filesystems to Docker
                downloadJob = utils.localDownloadJob(downloadJob, SHARED_WORKSPACE_ABSOLUTE + "/inputs/" + analysisIds.get(i), bams.get(i));

            } else if (GNOS.equalsIgnoreCase(downloadSource)) {

                // GET FROM INI

                downloadJob = utils.gnosDownloadJob(downloadJob, SHARED_WORKSPACE_ABSOLUTE + "/inputs", pemFile, gnosTimeoutMin,
                        gnosRetries, gnosServer, analysisIds.get(i), bams.get(i), gnosDownloadName);

            } else if (S3.equalsIgnoreCase(downloadSource)) {

                downloadJob = utils.s3DownloadJob(downloadJob, analysisIds.get(i), bams.get(i), allBamS3Urls.get(i), s3Key, s3SecretKey);

            } else {
                throw new RuntimeException("Don't know what download Type " + downloadSource + " is!");
            }

            downloadJob.addParent(previousJobPointer);
            // for now, make these sequential
            previousJobPointer = downloadJob;
        }
        return previousJobPointer;
    }

}
