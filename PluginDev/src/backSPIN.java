package src;


import java.awt.Component;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;

import com.treestar.flowjo.engine.EngineManager;
import com.treestar.flowjo.engine.ExternalEngine;
import com.treestar.flowjo.engine.FEML;
import com.treestar.flowjo.engine.Query;
import com.treestar.flowjo.engine.utility.EPluginHelper;
import com.treestar.flowjo.engine.utility.ExternalPopulationAlgorithmUtility;
import com.treestar.flowjo.engine.utility.RFlowCalculator;
import com.treestar.flowjo.engine.utility.R_Algorithm;
import com.treestar.flowjo.server.FJServerUtility;
import com.treestar.flowjo.server.ServerConfiguration;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.ExternalPopulationAlgorithmInterface;
import com.treestar.lib.data.StringUtil;
import com.treestar.lib.file.FJFileRef;
import com.treestar.lib.file.FJFileRefFactory;
import com.treestar.lib.file.FileUtil;
import com.treestar.lib.file.LocalFile;
import com.treestar.lib.file.Zipper;
import com.treestar.lib.file.uri.UriUtil;
import com.treestar.lib.fjml.FJML;
import com.treestar.lib.fjml.types.FileTypes;
import com.treestar.lib.graphics.PDFUtility;
import com.treestar.lib.gui.FJButton;
import com.treestar.lib.gui.FontUtil;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.actions.FJAction;
import com.treestar.lib.prefs.HomeEnv;
import com.treestar.lib.util.FJCommandLineUtil;
import com.treestar.lib.xml.GatingML;
import com.treestar.lib.xml.SElement;

public class backSPIN implements ExternalPopulationAlgorithmInterface {


	private static final String pyScript_backSPIN_Template = "pyScript.backSPIN.Template.py";
	//	private static final String SPADE_SCRIPT_FILE_NAME = "RScript.SPADE.Template.R";

	private static final String gVersion = "1.0";
	public static String backSPIN = "BackSPIN";
	public static final String gHtmlFile = "htmlFile";
	public static final String gZipFile = "zipFile";
	private static final String ShowTrees = "Show Trees";
	private static final String GetZip = "Get Zip File";
	private JCheckBox boolVerbose;
	private boolean boolVerboseOut;
	// Constructor
	// @param String for plugin
	//public backSPIN() {		super(backSPIN);	}

	// Overriding
	// @spade return string "backSPIN"
	@Override 
	public String getName() {	return backSPIN;   }

	public SElement getElement() {
		SElement result = super.getElement();
		return result;
	}
	public void setElement(SElement element) {

		super.setElement(element);
	}
	
	/*
	 * This method returns a list of parameter names when supplying
	 * the data values for the input file to your algorithm
	 * When your algorithm method is invoked, the input data file
	 * will contain FCS parameters or CSV columns for each of the parameters 
	 * in this list.
	 * (non-Javadoc)
	 * @see com.treestar.flowjo.engine.utility.R_Algorithm#getParameters()
	 */
	public List<String> getParameters(){
		
		//TODO: Plug this into the user interface to 
		return fParameterNames;
		
	}
	protected List<Component> getPromptComponents(SElement algorithmElement, List<String> parameterNames)
	{
		List<Component> result = super.getPromptComponents(algorithmElement, parameterNames);

		boolVerbose = new JCheckBox("Show Output in Terminal");
		boolVerbose.setToolTipText("Shows Algorithm Progress in Terminal");
		boolVerbose.setSelected(boolVerboseOut);
		result.add(boolVerbose);

		FJButton webButton = null;
		FJButton zipButton = null;
		FJButton outButton = null;
		
			webButton = new FJButton(new FJAction(this, ShowTrees), FontUtil.dlog10);
		if (!fRoutFile.isEmpty())
			outButton = createShowOutputButton();
		if (webButton != null || zipButton != null || outButton != null)
		{
			HBox box = new HBox(Box.createHorizontalGlue(), webButton, zipButton, outButton, Box.createHorizontalGlue());
			result.add(box);
		}
		return result;
	}
	//
	private static File ScriptFile = null;
	private static String backSPIN_SCRIPT_FILE_NAME = "backSPIN.py";


	/*
	 * This method fetches the backSPIN script to run
	 * 
	 */
	private File getBackSPINscript(File folder)
	{
		if (ScriptFile == null || !ScriptFile.exists())
		{
			InputStream stream = getClass().getClassLoader().getResourceAsStream("scripts/" + backSPIN_SCRIPT_FILE_NAME);
			if (stream != null)
				try {
					File filer = new File(folder, backSPIN_SCRIPT_FILE_NAME);
					FileUtil.copyStreamToFile(stream, filer);
					ScriptFile = filer;
				} catch (Exception e) { }
		}
		return ScriptFile;
	}
	/*
	 * 
	 */
	
	private static String CEF_SCRIPT_FILE_NAME = "CEF_Tools.py";
	private File getCEFtools()
	{
		String rootFolder = new HomeEnv().getUserTempFolder();
		if (ScriptFile == null || !ScriptFile.exists())
		{
			InputStream stream = getClass().getClassLoader().getResourceAsStream("scripts/" + backSPIN_SCRIPT_FILE_NAME);
			if (stream != null)
				try {
					File filer = new File(rootFolder, CEF_SCRIPT_FILE_NAME);
					FileUtil.copyStreamToFile(stream, filer);
					ScriptFile = filer;
				} catch (Exception e) { }
		}
		return ScriptFile;
	}
	/**
	 * This method uses the input sample file's name to generate a hash string.  If the integer hash is negative,
	 * it is converted to positive.
	 * @param sampleFile The input sample file
	 * @return The hash of the sample file name
	 */
	private static String getUniqueHash(File sampleFile)
	{
		int hash = sampleFile.getName().hashCode();
		if (hash < 0)
			hash = 0 - hash;
		return Integer.toString(hash);
	}

	private static File outputFolder;
	/**
	 * This method creates a new subfolder from the input output folder.
	 * @param outputFolder The top level output folder input to invokeAlgorithm
	 * @param sampleFile The input sample file
	 * @return A new subfolder where to place files
	 */
	private static File makeOutputSubfolder(File outputFolder, File sampleFile)
	{
		File result = new File(outputFolder, getUniqueHash(sampleFile));
		result.mkdirs();
		return result;
	}
	public static void executeCMD(String input){
		try
		{
			if(input!="")
			{
				Process proc = Runtime.getRuntime().exec(input);
			}
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static SElement convertCSVtoCEF(String inputFile){
		
		
		if(outputFolder.exists()){
			
		}
		return null;
	}
	// Method calls out execute the python script
	public static SElement performBackSPIN(String outFolderName, String inputFile, String outputFile){
		//create a folder
		if (outFolderName == null || outFolderName.isEmpty())
		{
			outFolderName = new HomeEnv().getUserTempFolder();
			File outFN = new File(outFolderName);
			File fName = new File("backSPIN");
			makeOutputSubfolder(outFN, fName);
			
		}
		try{

			//			   			-i [inputfile]
			//					   --input=[inputfile]
			//					          Path of the cef formatted tab delimited file.
			//					          Rows should be genes and columns single cells/samples.
			//					          For further information on the cef format visit:
			//					          https://github.com/linnarsson-lab/ceftools
			String inFile = inputFile; 
			//					   -o [outputfile]
			//					   --output=[outputfile]
			//					          The name of the file to which the output will be written
			String outFile = outputFile; 
			//					   -d [int]
			//					          Depth/Number of levels: The number of nested splits that will 
			//							  be tried by the algorithm
			int numLevels = 0;
			String levels = "-d "+numLevels;
			//					   -t [int]
			//					          Number of the iterations used in the preparatory SPIN.
			//					          Defaults to 10
			int numIterations = 10;
			String iterations = "-t "+numIterations;
			//					   -f [int]  
			//					   Feature selection is performed before BackSPIN. Argument controls how many genes are seleceted.
			//					   Selection is based on expected noise (a curve fit to the CV-vs-mean plot).
			int numFeatures = 0;
			String features = "-f "+numFeatures;

			//					   -s [float]
			//					          Controls the decrease rate of the width parameter used in the preparatory SPIN.
			//					          Smaller values will increase the number of SPIN iterations and result in higher 
			//					          precision in the first step but longer execution time.
			//					          Defaults to 0.05
			double widthSPIN = 0.05;
			String wSPIN = "-s "+widthSPIN;
			//					   -T [int]
			//					          Number of the iterations used for every width parameter.
			//					          Does not apply on the first run (use -t instead)
			//					          Defaults to 8
			int numWidthIters = 8;
			String wIters = "-T "+numWidthIters;
			//					   -S [float]
			//					          Controls the decrease rate of the width parameter.
			//					          Smaller values will increase the number of SPIN iterations and result in higher 
			//					          precision but longer execution time.
			//					          Does not apply on the first run (use -s instead)
			//					          Defaults to 0.25
			double widthDecrease = 0.25;
			String wDec = "-S " +widthDecrease;
			//					   -g [int]
			//					          Minimal number of genes that a group must contain for splitting to be allowed.
			//					          Defaults to 2
			int minGene = 2;
			String minimumGene = "-g "+minGene;
			//					   -c [int]
			//					          Minimal number of cells that a group must contain for splitting to be allowed.
			//					          Defaults to 2
			int minCell = 2;
			String minimumCell = "-c "+minCell;
			//					   -k [float]
			//					          Minimum score that a breaking point has to reach to be suitable for splitting.
			//					          Defaults to 1.15
			double minSplit = 1.15;
			String minimumSplit = "-k "+minSplit;
			//					   -r [float]
			//					          If the difference between the average expression of two groups is lower than threshold the algorythm 
			//					          uses higly correlated genes to assign the gene to one of the two groups
			//					          Defaults to 0.2
			double avgExpDiff = 0.2;
			String averageExpDiff = "-r "+avgExpDiff;
			//					   -b [axisvalue]
			//					          Run normal SPIN instead of backSPIN.
			//					          Normal spin accepts the parameters -T -S
			//					          An axis value 0 to only sort genes (rows), 1 to only sort cells (columns) or 'both' for both
			//					          must be passed
			boolean onlySPIN = false; 
			if(onlySPIN == true){
				String nSPINt = "-T ";
				String nSPINs = "-S ";
				boolean bothGeneCells = false; 
				int onlyGene = 0;
				int onlyCells = 1;
			}
			//					   -v  
			//					          Verbose. Print  to the stdoutput extra details of what is happening
			String verboseSPIN = " -v ";

			// Setup process to execute python script with parameters
		}catch(Exception e)
		{
			e.printStackTrace();
		}

		return null;
	}
	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlElem, File sampleFile, File outputFolder) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();


		return result;
	}
	private static Icon gIcon = null;
	@Override	public Icon getIcon()
	{
		if (gIcon == null)
		{
			URL url = getClass().getClassLoader().getResource("images/backSPIN.png");
			if (url != null)
				gIcon = new ImageIcon(url);
		}
		return gIcon;
	}
	protected String getIconName()	{	return "images/spade.png";	}

	/*
	 * Plugin accepts CSV with scale values only
	 */
	public ExportFileTypes useExportFileType() { return ExportFileTypes.CSV_SCALE; }

	@Override
	public boolean promptForOptions(SElement fcmlQueryElement,
			List<String> parameterNames) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}
}
