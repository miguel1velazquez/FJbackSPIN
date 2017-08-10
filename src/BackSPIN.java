
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import com.flowjo.lib.parameters.ParameterSelectionPanel;
import com.flowjo.lib.parameters.ParameterSelectionPanel.eParameterSelectionMode;
import com.flowjo.lib.parameters.ParameterSet;
import com.flowjo.lib.parameters.ParameterSetCollection;
import com.flowjo.lib.parameters.ParameterSetMgrInterface;
import com.treestar.flowjo.engine.EngineManager;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.core.SeqGeqExternalAlgorithmResults;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.numberfields.RangedIntegerTextField;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.text.FJTextField;
import com.treestar.lib.xml.SElement;

/**
 * This class illustrates how to implement a SeqGeq population plugin, to show how to 
 * 1) read the various input file formats, 
 * 2) create new parameters by writing a mergeable CSV file, 
 * 3) define a new subpopulation using GatingML
 * 4) create a new gene set
 * 5) use ParameterSelectionPanel to select gene sets and genes
 */
public class BackSPIN implements PopulationPluginInterface {

	
	// Variables to be stored during Set & Get Element
	// version to be returned
	private static final String gVersion = "1.0";
	private static boolean runAgain = false;
	// Parameter names to use for backSPIN plugin
	private List<String> parameterNames = new ArrayList<String>();
	private int clusterCount = 0;
	private String backSPIN_SCRIPT_PATH = "bsScripts/backSPIN.py";
	private String cefReader_SCRIPT_PATH = "bsScripts/cefReader.py";
	private String cefTools_SCRIPT_PATH = "bsScripts/Cef_tools.py";
	private String cefWriter_SCRIPT_Path = "bsScripts/cefWriter.py";

	private static int noisy_Genes_reduction = 0; 
	private static int numLevels = 3;  //Default number of Levels to Display
	private static Icon gIcon = null;
	private static String bsParamName_RunID =  "1";
// This method gets the name to be displayed by SeqGeq.

	@Override public String getName() {	return "BackSPIN";	}
// Gets version to be returned by SeqGeq
	@Override public String getVersion() {	return gVersion;		}

	@Override public ExportFileTypes useExportFileType() 
	{	
		return ExportFileTypes.CSV_PIR_SCALE;		
	}

	@Override public List<String> getParameters() {	return parameterNames;	}

	/*
	 * This method sets the Icon for SeqGeq to use in the 
	 * @return ImageIcon object of png
	 */

	@Override public Icon getIcon()
	{
		if (gIcon == null)
		{
			URL url = getClass()
					.getClassLoader()
					.getResource("images/backSPINy.png");
			if (url != null)
				gIcon = new ImageIcon(url);
		}
		return gIcon;
	}
	@Override public SElement getElement() 
	{
		SElement result = new SElement(getName());
		// store the parameters the user selected
		if (!parameterNames.isEmpty())
		{
			SElement elem = new SElement("Parameters");
			result.addContent(elem);
			for (String pName : parameterNames)
			{
				SElement e = new SElement("P");
				e.setString("name", pName);
				elem.addContent(e);
			}
		}
		result.setInt("numLevels", numLevels);
		result.setInt("noisyGenes", noisy_Genes_reduction);
		result.setString("BSrunID", bsParamName_RunID);
//		result.setString("exportType", value);
		result.setBool("runAgain", runAgain);
		return result;
	}
	@Override public void setElement(SElement element) {
		SElement params = element.getChild("Parameters");
		if (params == null)
			return;
		parameterNames.clear();
		for (SElement elem : params.getChildren())
		{
			parameterNames.add(elem.getString("name"));
		}
		bsParamName_RunID = element.getString("BSrunID", bsParamName_RunID);
		numLevels = element.getInt("numLevels", numLevels);
		noisy_Genes_reduction = element.getInt("noisyGenes", noisy_Genes_reduction);
		runAgain = element.getBool("runAgain");
//		String exportType = element.getString("exportType");
	}

	/* (non-Javadoc)
	 * @see com.treestar.lib.core.ExternalPopulationAlgorithmInterface#promptForOptions(com.treestar.lib.xml.SElement, java.util.List)
	 * Construct a dialog to prompt the user for the parameters, the format of the input file, and the gene sum threshold to use to create a categorical parameter.
	 */
	@Override
	public boolean promptForOptions(SElement fcmlQueryElement, List<String> pNames) {
		// Use a helper method to get a ParameterSetMgrInterface, used by ParameterSelectionPanel
		ParameterSetMgrInterface mgr = PluginHelper.getParameterSetMgr(fcmlQueryElement);
		// Check if the mgr could not be created, if null, end and return false.
		if (mgr == null)
			return false;		
		// Setup a list of objects were we can add the components of our GUI 
		List<Object> guiObjects = new ArrayList<Object>();
		// Create a FlowJo label, similar to a display area for a short text string or an image, or both.
		FJLabel explainTextFJLabel = new FJLabel();
		// Add the FJlabel to the guiObjects list.
		// Create a string called "textToDisplay", start in HTML, add the <body> html body 
		String textToDisplay = "<html><body>";
		// Add line
		textToDisplay += "<h4><center>BackSPIN Bi-Clustering Algorithm<center></h4>";
		// Add an unordered list
		textToDisplay += "<ul>";
		// Add list item
		textToDisplay += "<li>Utilizes BackSPIN algorithm to find Gene Sets and Cluster pairs</li>";
		textToDisplay += "<li>Creates Gates on Clusters found.</li>";
		textToDisplay += "<li>Defines Gene Sets corresponding to a Cluster.</li>";
		// End unordered list
		textToDisplay += "</ul>";
		textToDisplay += "</body></html>";
		// Set the textToDisplay String object to the explainTextFJLabel object.
		explainTextFJLabel.setText(textToDisplay);
		guiObjects.add(explainTextFJLabel);

		FJLabel LevelLabel = new FJLabel("Maximum Levels to Explore  ");
		String Leveltip = "Generally 3-5 levels, depending on expected clusters. For ex, 3 levels will give 2^3.";
		LevelLabel.setToolTipText(Leveltip);
		RangedIntegerTextField numLevelsField = new RangedIntegerTextField(2, 10);
		numLevelsField.setInt(numLevels);
		numLevelsField.setToolTipText(Leveltip);
		GuiFactory.setSizes(numLevelsField, new Dimension(50, 25));
		HBox Levelbox = new HBox(Box.createHorizontalGlue(), LevelLabel, numLevelsField, Box.createHorizontalGlue());
		guiObjects.add(Levelbox);

		FJLabel nGeneLabel = new FJLabel("Gene CV vs. Mean Filtering  ");

		String nGeneTip = "Top noisy genes in a CV vs. Mean plot, creates a fit then finds as many genes as desired in Gene filter field. Improves performance and time.";
		nGeneLabel.setToolTipText(nGeneTip);
		RangedIntegerTextField noisyGeneField = new RangedIntegerTextField(0, 2000000);
		noisyGeneField.setInt(noisy_Genes_reduction);
		noisyGeneField.setToolTipText(nGeneTip);
		GuiFactory.setSizes(noisyGeneField, new Dimension(50, 25));
		HBox nGenebox = new HBox(Box.createHorizontalGlue(), nGeneLabel, noisyGeneField, Box.createHorizontalGlue());
		guiObjects.add(nGenebox);

		String tooltip = "Enter suffix for the current BackSPIN run, this will be used to create a unique backSPIN parameter for each run";
		FJLabel runIDLabel = new FJLabel("Run Identification Number   ");
		FJTextField bsRunIDField = new FJTextField();
		bsRunIDField.setText(bsParamName_RunID);
		bsRunIDField.setToolTipText(tooltip);
		GuiFactory.setSizes(bsRunIDField, new Dimension(50, 25));
		HBox runIDBox = new HBox(Box.createHorizontalGlue(), runIDLabel, bsRunIDField, Box.createHorizontalGlue());
		guiObjects.add(runIDBox);
		ParameterSelectionPanel pane = new ParameterSelectionPanel(mgr, 
										eParameterSelectionMode.WithSetsAndParameters, 
										true, false, false, true);
		Dimension dim = new Dimension(300, 500);
		pane.setMaximumSize(dim);
		pane.setMinimumSize(dim);
		pane.setPreferredSize(dim);

		pane.setSelectedParameters(parameterNames);
		parameterNames.clear();
		guiObjects.add(pane);
		
		FJLabel explainTextFJLabel1 = new FJLabel();
		// Add the FJlabel to the guiObjects list.
		guiObjects.add(explainTextFJLabel1);
		// Create a string called "textToDisplay", start in HTML, add the <body> html body 
		String textToDisplay1 = "<html><body>";
		textToDisplay1 +=   "<b> Citation </b><br>";
		textToDisplay1 += 	"<b>Algorithm developed by Amit Zeisel in Matlab and Python by Gioele La Manno at the Karolinska Linnarsson</b><br>";
		textToDisplay1 += 	"<i>Article: Zeisel et al. Cell types in the mouse cortex and hippocampus revealed by single-cell RNA-seq Science 2015</i><br>";
		textToDisplay1 += 	"(PMID: 25700174, doi: 10.1126/science.aaa1934)";
		// End html body. 
		textToDisplay1 += "</body></html>";
		// Set the textToDisplay String object to the explainTextFJLabel object.
		explainTextFJLabel1.setText(textToDisplay1);

		int option = JOptionPane.showConfirmDialog(null, 
				guiObjects.toArray(), "BackSPIN Plugin", 
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null);
		if (option == JOptionPane.OK_OPTION) {
			// User clicked OK, get all selected parameters
			parameterNames.addAll(pane.getParameterSelection());
			// Add 'CellId' so input data file will have it
			if (!parameterNames.contains("CellId")) {
				parameterNames.add("CellId");
			}
			// get other GUI inputs
			numLevels = numLevelsField.getInt();
			noisy_Genes_reduction = noisyGeneField.getInt();
			bsParamName_RunID = bsRunIDField.getText();
			if(parameterNames.contains(bsParamName_RunID) || parameterNames.contains("BackSPIN"+bsParamName_RunID)) 
			{
				bsParamName_RunID += "1";
			};
			
			runAgain = true;
			return true;
		}
		// If User did not select OK, then end plugin.
		return false;
	}

	public void copyCEFTools(String outputCEFTools) 
	{
		// Check if windows, then change the back and forward slashes.
		// "This is how FlowJo's flowMeans code is doing it; I just hope they tested it properly :-)" -JS
		InputStream scriptStream = BackSPIN.class.getResourceAsStream(cefTools_SCRIPT_PATH);
		StringWriter scriptWriter = new StringWriter();
		BufferedReader rTemplateReader = null;
		try {
			if (scriptStream != null) {rTemplateReader = new BufferedReader(new InputStreamReader(scriptStream));}
		} catch (Exception e) {e.printStackTrace();}

		String scriptLine;
		try {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputCEFTools)));
			while((scriptLine = rTemplateReader.readLine()) != null) 
			{
				scriptWriter.append(scriptLine).append('\n');
				bw.write(scriptLine);
				bw.newLine();
			}
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(rTemplateReader != null) {
			try { rTemplateReader.close(); }
			catch (Exception e) { e.printStackTrace(); }
		}
	}

	public void copyBackSPINpy(String backSPIN_Script_Destination) throws FileNotFoundException
	{

		InputStream scriptStream = BackSPIN.class.getResourceAsStream(backSPIN_SCRIPT_PATH);
		StringWriter scriptWriter = new StringWriter();
		BufferedReader rTemplateReader = null;
		try {
			rTemplateReader = new BufferedReader(new InputStreamReader(scriptStream));
		} catch (Exception e) {
			e.printStackTrace();
		}
		String scriptLine;
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(backSPIN_Script_Destination)));
		try {
			while((scriptLine = rTemplateReader.readLine()) != null) 
			{
				scriptWriter.append(scriptLine).append('\n');
				bw.write(scriptLine);
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(rTemplateReader != null) {
			try { rTemplateReader.close(); }
			catch (Exception e) { e.printStackTrace(); }
		}
	}

	public void createCefWriter(String SeqGeq_CSV_File, String BackSPIN_Input_CEF_fName, String cefWriter_Python_Location)
	{	
		InputStream scriptStream = BackSPIN.class.getResourceAsStream(cefWriter_SCRIPT_Path);
		BufferedReader rTemplateReader = null;
		try {rTemplateReader = new BufferedReader(new InputStreamReader(scriptStream));} catch (Exception e) {e.printStackTrace();}
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String scriptLine;
		StringWriter scriptWriter = new StringWriter();
		BufferedWriter bw = null;
		try {bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cefWriter_Python_Location)));} catch (FileNotFoundException e1) {e1.printStackTrace();}
		try {while((scriptLine = rTemplateReader.readLine()) != null) {
			scriptLine = scriptLine.replaceAll("SAMPLE_FILE_FROM_SEQGEQ", SeqGeq_CSV_File);
			scriptLine = scriptLine.replaceAll("BackSPIN_Input_CEF_fName", BackSPIN_Input_CEF_fName);
			scriptLine = scriptLine.replaceAll("DATE_TIME_SCRIPT_RAN", dateFormat.format(date));
			scriptWriter.append(scriptLine).append('\n');
			System.out.println(scriptLine);
			bw.write(scriptLine);
			bw.newLine();}
		bw.close();} catch (Exception e) {e.printStackTrace();}

		if(rTemplateReader != null) {
			try { rTemplateReader.close(); }
			catch (Exception e) { e.printStackTrace(); }
		}
	}
	public void createReadCEFscript(String cefReaderLocation, String bsCEFOutput, String clusterCSV, String geneSetCSV, String bsRUNID) throws FileNotFoundException
	{		
		InputStream scriptStream = BackSPIN.class.getResourceAsStream(cefReader_SCRIPT_PATH);
		BufferedReader rTemplateReader = null;
		try {
			rTemplateReader = new BufferedReader(new InputStreamReader(scriptStream));
		} catch (Exception e) {
			e.printStackTrace();
		}		
		StringWriter scriptWriter = new StringWriter();
		String scriptLine;
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cefReaderLocation)));
		try {
			while((scriptLine = rTemplateReader.readLine()) != null) 
			{
				scriptLine = scriptLine.replaceAll("BackSPIN_Output_CEF", bsCEFOutput);
				scriptLine = scriptLine.replaceAll("CLUSTERS_OUTFILE", clusterCSV);
				scriptLine = scriptLine.replaceAll("GENE_SET_OUTFILE", geneSetCSV);
				scriptLine = scriptLine.replaceAll("bRUNID", bsRUNID);

				scriptWriter.append(scriptLine).append('\n');
				System.out.println(scriptLine);
				bw.write(scriptLine);
				bw.newLine();
			}
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if(rTemplateReader != null) {
			try { rTemplateReader.close(); }
			catch (Exception e) { e.printStackTrace(); }
		}
	}

	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		// create an SeqGeqExternalAlgorithmResults so we can return a gene set
		SeqGeqExternalAlgorithmResults result = new SeqGeqExternalAlgorithmResults();
		if(runAgain) {
		System.out.println("If BackSPIN dependencies are not installed, enter password.");
		installBackSPIN();
	
		if(!findR()) {
			JOptionPane.showMessageDialog(null, "Python is not installed");
			return result;
		}
		// Check for at least 6 genes to run BackSPIN algorithm
		if (parameterNames.size() < 6){JOptionPane.showMessageDialog(null, "Select 6 or more genes, then try again");return result;}

		// Define all absolute locations of files and scripts/rewritten scripts.
		if (!outputFolder.exists()){result.setErrorMessage("Error: Could not create output folder.");return result;}
		if (!sampleFile.exists()){result.setErrorMessage("Error: Selected file not created - Check permissions, or save analysis and try again.");return result;}
		String SeqGeq_CSV_FileName = 		sampleFile.getAbsolutePath();
		String trimSampleName = 			sampleFile.getName().replaceAll(".csv", "").replaceAll(".ExtNode", "").replaceAll(".fcs", "").trim();
		String Absolute_Path_SampleName = 	outputFolder.getAbsolutePath()+"/"+trimSampleName;
		String BackSPIN_Input_CEF_fName = 	Absolute_Path_SampleName+".bsInputCEF.cef";
		String BackSPIN_Output_CEF_fName =  Absolute_Path_SampleName+".bsOutputCEF.cef";
		String GeneSets_Output_CSV_fName =  Absolute_Path_SampleName+".GeneSets.csv";
		String Cluster_Output_CSV_fName = 	Absolute_Path_SampleName+bsParamName_RunID+".Clusters.csv";
		String CEF_Tools_AbsPath_fName = 	outputFolder+"/Cef_tools.py";
		String cefWriter_Python_Location =  	Absolute_Path_SampleName+".bsCEFWriter.py";
		String backSPIN_Python_Script_AbsPath = 	outputFolder+"/backSPIN.py";
		String cefReader_Python_Script_AbsPath = 	Absolute_Path_SampleName+".bsCEFReader.py";
		String backSPIN_PARAM_RUNID = 	"backSPIN"+bsParamName_RunID;
		// Check if OS is Windows, in this case change back-slashes to forward-slashes. \\//endetta
		if(EngineManager.isWindows())
		{
			SeqGeq_CSV_FileName = 			SeqGeq_CSV_FileName.replaceAll("\\\\", "/");
			Absolute_Path_SampleName = 		Absolute_Path_SampleName.replaceAll("\\\\", "/");
			BackSPIN_Input_CEF_fName = 		BackSPIN_Input_CEF_fName.replaceAll("\\\\", "/");
			BackSPIN_Output_CEF_fName = 	BackSPIN_Output_CEF_fName.replaceAll("\\\\", "/");
			GeneSets_Output_CSV_fName = 	GeneSets_Output_CSV_fName.replaceAll("\\\\", "/");
			Cluster_Output_CSV_fName = 		Cluster_Output_CSV_fName.replaceAll("\\\\", "/");
			CEF_Tools_AbsPath_fName = 		CEF_Tools_AbsPath_fName.replaceAll("\\\\", "/");
			cefWriter_Python_Location =		cefWriter_Python_Location.replaceAll("\\\\", "/");
			backSPIN_Python_Script_AbsPath =backSPIN_Python_Script_AbsPath.replaceAll("\\\\", "/");
			cefReader_Python_Script_AbsPath=cefReader_Python_Script_AbsPath.replaceAll("\\\\", "/");
		}
		List<File> listOfFiles = new ArrayList<>();
		copyCEFTools(CEF_Tools_AbsPath_fName);
		File ceftoolFile = new File(CEF_Tools_AbsPath_fName);
		listOfFiles.add(ceftoolFile);
		if (!ceftoolFile.exists()) {result.setErrorMessage("Error: Could not write CEF tools to "+outputFolder+". Check your permissions to your GeqZip dierctory.");}
		createCefWriter(SeqGeq_CSV_FileName, BackSPIN_Input_CEF_fName, cefWriter_Python_Location);
		File cefWriterHomebase = new File(cefWriter_Python_Location);
		listOfFiles.add(cefWriterHomebase);
		if (!cefWriterHomebase.exists()) {result.setErrorMessage("Error: Could not not write CSV into backSPIN format. Check that Python is correctly installed.");}
		executePython(cefWriter_Python_Location);
		File cefWriter_Python_Location_File = new File(cefWriter_Python_Location);
		listOfFiles.add(cefWriter_Python_Location_File);
		try {copyBackSPINpy(backSPIN_Python_Script_AbsPath);} 
		catch (FileNotFoundException e) {e.printStackTrace();}
		File backSPIN_Python_Script_AbsPath_File = new File(backSPIN_Python_Script_AbsPath);
		listOfFiles.add(backSPIN_Python_Script_AbsPath_File);
		executePython(composeBackSPINcommand(backSPIN_Python_Script_AbsPath,BackSPIN_Input_CEF_fName,BackSPIN_Output_CEF_fName, numLevels, noisy_Genes_reduction));
		try {
			createReadCEFscript(cefReader_Python_Script_AbsPath, BackSPIN_Output_CEF_fName,Cluster_Output_CSV_fName,GeneSets_Output_CSV_fName, backSPIN_PARAM_RUNID);
			} 
		catch (FileNotFoundException e) {JOptionPane.showConfirmDialog(null, "Could not read CEF");e.printStackTrace();}
		executePython(cefReader_Python_Script_AbsPath);
		result.setCSVFile(new File(Cluster_Output_CSV_fName));
		Map<String, Integer> BackSPIN_GeneSet_Map = null;
		BackSPIN_GeneSet_Map = getBackSPINGeneSets(GeneSets_Output_CSV_fName, result);
		addGeneSetsToResult(fcmlQueryElement, result, BackSPIN_GeneSet_Map);
		addGatingML(result, clusterCount, backSPIN_PARAM_RUNID);
		listOfFiles.add(sampleFile);
		for(File fl : listOfFiles){fl.delete();}
		}
		return result;
	}
	private String composeBackSPINcommand(String backSPINscript, String backSPINinputCEF, String backSPINoutputCEF, int levels, int nGenes) {
		String bsCommands = null;
		if(nGenes>0)
		{
			bsCommands = backSPINscript+" -i "+backSPINinputCEF+" -o "+backSPINoutputCEF+" -d "+levels+" -f "+nGenes+" -v";
		}
		else{
			bsCommands = backSPINscript+" -i "+backSPINinputCEF+" -o "+backSPINoutputCEF+" -d "+levels+" -v";
		}
		System.out.println("BackSPIN CML Call: "+bsCommands);
		return bsCommands;
	}
	/*
	 *  This method creates FlowJo's XML gating markup language, number of clusters and a run ID
	 *  to create gates on a new parameter.
	 *  @param ExternalAlgorithmResults
	 *  @param int clusters
	 *  @param String runID
	 *  return void
	 */
	private void addGatingML(ExternalAlgorithmResults result, int clusters, String runID)
	{

		SElement gate = new SElement("gating:Gating-ML");
//		if(clusters>10){
			for (int i = 0; i <= clusters; i++){ 
				int val = i;
				// create the XML elements for a 1-D range gate
				SElement rangeGateElem = new SElement("gating:RectangleGate");
				rangeGateElem.setString("gating:id", "BackSPIN_Run_"+bsParamName_RunID+"_Cluster_"+i);
				gate.addContent(rangeGateElem);
				// create the dimension XML element
				SElement dimElem = new SElement("gating:dimension");
				dimElem.setDouble("gating:min", val-0.4);
				dimElem.setDouble("gating:max", val+0.4);
				rangeGateElem.addContent(dimElem);
				// create the parameter name XML element
				SElement fcsDimElem = new SElement("data-type:fcs-dimension");
				fcsDimElem.setString("data-type:name", runID);
				dimElem.addContent(fcsDimElem);
			}
		result.setGatingML(gate.toString());
	}

	public void installBackSPIN(){

		try{
			if(!checkifBSinstalled()){
				
				String[] args = new String[5];
				args[0] = "sudo easy_install pip";
				args[1] = "sudo pip install future";
				for(String str : args){
					Process p = Runtime.getRuntime().exec(str);
					BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
					String s = br.readLine(); 
					while((s=br.readLine())!=null){
						System.out.println(s);
					}
					p.waitFor();		    
				}
			}
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
	}
	public boolean checkifBSinstalled() throws IOException
	{
		try{
			Process p = Runtime.getRuntime().exec("python -c \"import future\"");
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String s = br.readLine(); 
			while((s=br.readLine())!=null){
				if(s=="0"){
					System.out.println("Future installed: "+s);
					return true;
				}
				System.out.println(s);
			}
			p.waitFor();		    
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
		return false;
	}
	public void executePython(String pyScript){
		try{
			Process p = Runtime.getRuntime().exec("python "+pyScript);
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String s = br.readLine(); 
			while((s=br.readLine())!=null){
				System.out.println(s);
			}
			p.waitFor();		    
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
	}

	private void addGeneSetsToResult(SElement fcmlQueryElement, SeqGeqExternalAlgorithmResults result, Map<String, Integer> bsGeneSets)
	{

		String sampleName = FJPluginHelper.getSampleName(fcmlQueryElement).replace(".csv", "").replace(".txt", "").replace(".fcs", "");

//		Map<String, Integer> sortedMap = sortByValue(bsGeneSets);

		Map<Integer, List<String>> geneSets = new HashMap<>();

		for (String geneKey : bsGeneSets.keySet()) {
			geneSets.computeIfAbsent(bsGeneSets.get(geneKey), v -> new ArrayList<>()).add(geneKey);
		}
		ParameterSetCollection psc = new ParameterSetCollection("BackSPIN_"+sampleName+" Run "+bsParamName_RunID);
		for (int i = 0; i < geneSets.size(); i++) {

			ParameterSet pSet = new ParameterSet("BackSPIN_Run_"+bsParamName_RunID+"_GeneSet_"+i, geneSets.get(i));
			psc.addParameterSet(pSet);
			System.out.println(pSet.getParameterNames());
		}
		result.addParameterSetCollection(psc);
	}

	private Map<String, Integer> getBackSPINGeneSets(String sampleFile, SeqGeqExternalAlgorithmResults result)
	{
		Map<String, Integer> clustMap = new HashMap<String, Integer>();
//		int count = 0;
		int levelCounter = 0;
		BufferedReader sampleFileReader = null;
		try {
			sampleFileReader = new BufferedReader(new FileReader(sampleFile));
			boolean wh = true; 
			// now start reading data rows
			while (wh != false)
			{
				String gene = null;
				int lastLevel = 0;
				String line = sampleFileReader.readLine();
				if(line==null){
					wh = true;
					break;
				}
				StringTokenizer tokenizer = new StringTokenizer(line, ",");
				if (tokenizer.hasMoreTokens()) // first column is gene
				{
					levelCounter = tokenizer.countTokens()-1;
					gene = tokenizer.nextToken();
					StringBuilder sb = new StringBuilder(gene);
					sb.delete(0, 2);
					sb.delete(sb.toString().length()-2, sb.toString().length());
					gene = sb.toString();
					//					System.out.println("CSV Gene: "+gene);

				}
				// remaining columns are gene counts
				while (tokenizer.hasMoreTokens())
				{
					// Get level
					String valStr = tokenizer.nextToken();
					try {
						int val = Integer.parseInt(valStr);
						lastLevel = val;
//						count++;
					}
					catch (NumberFormatException e) { 
						System.err.println("Number Format Exception");
					} 
				}
				clustMap.put(gene, lastLevel);
				clusterCount = lastLevel;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally
		{
			numLevels = levelCounter;

			if (sampleFileReader != null)
				try {
					sampleFileReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		return clustMap;
	}
	public boolean findR() {
		String command = "python -V\n";
		String response = "";
		ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
		pb.redirectErrorStream(true);
		System.out.println("Linux command: " + command);
		try {
			Process shell = pb.start();
			if (true) {
				// To capture output from the shell
				InputStream shellIn = shell.getInputStream();
				// Wait for the shell to finish and get the return code
				int shellExitStatus = shell.waitFor();
				if(shellExitStatus == 0) {
//					System.out.println("Exited shell");
				}else if(shellExitStatus == 1) {
					System.out.println("Could not Exit shell");
				}
				response = convertStreamToStr(shellIn);
				shellIn.close();
			}
		}
		catch (IOException e) {
			System.out.println("Error occured while executing Linux command. Error Description: "
					+ e.getMessage());
		}
		catch (InterruptedException e) {
			System.out.println("Error occured while executing Linux command. Error Description: "
					+ e.getMessage());
		}
		String installed = "Python 2.7";
		String notInstalled = "Not Here";
		if(response.toLowerCase().contains(installed.toLowerCase())){
			System.out.print("Response: "+response);
			System.out.println(installed+" is Installed");
			return true;
		}
		else if(response.toLowerCase().contains(notInstalled.toLowerCase())){
			System.out.print("Response: "+response);
			System.err.println("Python is NOT Installed or Found at default location");
			return false;
		}
		return false;
	}

	/*
	 * To convert the InputStream to String we use the Reader.read(char[]
	 * buffer) method. We iterate until the Reader return -1 which means
	 * there's no more data to read. We use the StringWriter class to
	 * produce the string.
	 */

	public static String convertStreamToStr(InputStream is) throws IOException {

		if (is != null) {
			Writer writer = new StringWriter();

			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is,
						"UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		}
		else {
			return "";
		}
	}
}