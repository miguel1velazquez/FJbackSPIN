////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2015 Josef Spidlen, Ph.D.
//
// License
// The software is distributed under the terms of the 
// Artistic License 2.0
// http://www.r-project.org/Licenses/Artistic-2.0
// 
// Disclaimer
// This software and documentation come with no warranties of any kind.
// This software is provided "as is" and any express or implied 
// warranties, including, but not limited to, the implied warranties of
// merchantability and fitness for a particular purpose are disclaimed.
// In no event shall the  copyright holder be liable for any direct, 
// indirect, incidental, special, exemplary, or consequential damages
// (including but not limited to, procurement of substitute goods or 
// services; loss of use, data or profits; or business interruption)
// however caused and on any theory of liability, whether in contract,
// strict liability, or tort arising in any way out of the use of this 
// software.    
//////////////////////////////////////////////////////////////////////////////

//package ca.bccrc.flowjo;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JScrollPane;

import com.treestar.flowjo.engine.utility.R_Algorithm;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.data.StringUtil;
import com.treestar.lib.gui.FJList;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.numberfields.RangedDoubleTextField;
import com.treestar.lib.gui.numberfields.RangedIntegerTextField;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.xml.SElement;

import ca.bccrc.flowjo.utils.DoubleNumberFormatter;

public class bSPIN extends R_Algorithm {

	private static final String pluginVersion = "1.0";
	private static final String pluginName = "BackSPIN";
	
	private RangedDoubleTextField parBinSizeField = null;
    private RangedIntegerTextField parCellCutoffField = null;
    private RangedDoubleTextField parCutoffField = null;
    private RangedDoubleTextField parMaxFcField = null;
    
    private static final int fixedLabelWidth  = 75;
    private static final int fixedFieldWidth  = 75;
    private static final int fixedLabelHeigth = 25;
    private static final int fixedFieldHeigth = 25;
    private static final int hSpaceHeigth = 5;
    
    private static final String sLabelBinSize    = "Bin size (0-1)";
    private static final String sLabelCellCutoff = "Cell Cutoff";
    private static final String sLabelCutoff     = "Cutoff";
    private static final String sLabelMaxFc      = "Max Fc";
    
    private static final String sBinSizeHelp    = "The bin size as proportion of cells, typically between 0.01 and 0.2.";
    private static final String sCellCutoffHelp = "The number of cells to be used for a cut off, typically between 300 and 800.";
    private static final String sCutoffHelp     = "The cutoff quantile as a value from 0 to 1, or a direct cutoff threshold (>1).";
    private static final String sMaxFcHelp      = "Maximum allowed relative increase from mean norm of presumed good data.";
    
    public static final String sOptionNameBinSize    = "BinSize";
    public static final String sOptionNameCellCutoff = "CellCutoff";
    public static final String sOptionNameCutoff     = "Cutoff";
    public static final String sOptionNameMaxFc      = "MaxFc";
    
    private static final String channelsLabelLine1 = "FCS channels to be checked by flowClean. Select multiple items by pressing";
    private static final String channelsLabelLine2 = "the Shift key or toggle items by holding the Ctrl (or Cmd) keys. The Time";
    private static final String channelsLabelLine3 = "channel must exist in order for flowClean to be able to check the data set.";
    
    private static final String citingLabelLine1   = "Please cite this paper if you use the BackSPIN algorithm in your work:";
    private static final String citingLabelLine2   = "Amit Zeisel, Ana Munoz-Manchado, Sten Linnarsson, Gioele La Manno :";
    private static final String citingLabelLine3   = "Cell types in the mouse cortex and hippocampus revealed by single-cell RNA-seq";
    private static final String citingLabelLine4   = "Science | 06 Mar 2015 : Vol. 347, Issue 6226, pp. 1138-1142, DOI: 10.1126/science.aaa1934";
    
    protected static final String bsICON = "images/backSPIN.png";
    
    public bSPIN() 
	{
		super(pluginName);
	}

	public String getName() 
	{
		return(pluginName);
	}

	public String getVersion() 
	{
		return(pluginVersion);
	}
	
	@Override
    protected String getIconName()
    {
        return bsICON;
    }
	
	@Override
	protected boolean showClusterInputField()
	{
		return false;
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

		// TODO: For testing purposes only, syso.
		System.out.println(fParameterNames);
		return fParameterNames;
		
	}
	
	/*
	 * This method executes a command to the system
	 * @param name of script
	 * @param Type of script
	 */
	public static void executeCMD(String input, String type){
		try
		{
			if(input!="" && type.toLowerCase()=="backSPIN")
			{
				Process proc = Runtime.getRuntime().exec("backspin "+input);
			}
			else if(input!="" && type.toLowerCase()=="CEF"){
				Process proc = Runtime.getRuntime().exec(input);
			}
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	 * This method formulates the backspin command from prompt options
	 * then calls on the backspin.py script with the inputs.
	 */
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
	/*

	 * 
	 */
	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_SCALE;
	}

	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) 
	{
        ExternalAlgorithmResults results = new ExternalAlgorithmResults();
        if(!sampleFile.exists()) 
        {
            results.setErrorMessage("CEF Input was Not Found");
            return results;
        } 
        else
        {
            checkUseExistingFiles(fcmlQueryElement);
            String sampleName = StringUtil.rtrim(sampleFile.getName(), ".fcs");
            //FlowCleanRFlowCalc calculator = new FlowCleanRFlowCalc();
           // File fcsCleanResult = calculator.performFCSClean(sampleFile, sampleName, preprocessCompParameterNames(), fOptions, outputFolder.getAbsolutePath(), useExistingFiles());
           // calculator.deleteScriptFile();
           // checkROutFile(calculator);
           // results.setCSVFile(fcsCleanResult);
            return results;
        }
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	protected List<Component> getPromptComponents(SElement selement, List<String> list)
    {
        ArrayList<Component> componentList = new ArrayList<Component>();
        
        // 
        FJLabel fjLabel1 = new FJLabel(channelsLabelLine1);
        FJLabel fjLabel2 = new FJLabel(channelsLabelLine2);
        FJLabel fjLabel3 = new FJLabel(channelsLabelLine3);
        
        // fParameterNameList = new FJList(new DefaultListModel<Component>());
        // FlowJo advised against using DefaultListModel<Component>() due to compatibility issues. Following the advise...
        fParameterNameList = new FJList(new DefaultListModel());
        fParameterNameList.setSelectionMode(2);
        JScrollPane parListScrollPane = new JScrollPane(fParameterNameList);
        GuiFactory.setSizes(parListScrollPane, new Dimension(100, Math.min(200, list.size() * 20)));
        componentList.add(fjLabel1);
        componentList.add(fjLabel2);
        componentList.add(fjLabel3);
        componentList.add(parListScrollPane);
        
        // Default parameter values
        double parBinSize = 0.01;
        int parCellCutoff = 500;
        double parCutoff = 0.5; 
        double parMaxFc = 1.3;
        
        // If there are option set already (e.g., from the workspace), then
        // let's retrieve those and use them instead of defaults.
        Iterator<SElement> iterator = selement.getChildren("Option").iterator();
        while(iterator.hasNext()) {
            SElement option = iterator.next();
            
            double savedParBinSize = option.getDouble(sOptionNameBinSize, -1);
            if(savedParBinSize > 0 && savedParBinSize <= 1) parBinSize = savedParBinSize;
            
            int savedParCellCutoff = option.getInt(sOptionNameCellCutoff, -1);
            if(savedParCellCutoff > 0 && savedParCellCutoff <= 2147483647) parCellCutoff = savedParCellCutoff;
            
            double savedParCutoff = option.getDouble(sOptionNameCutoff, -1);
            if(savedParCutoff > 0) parCutoff = savedParCutoff;
            
            double savedparMaxFc = option.getDouble(sOptionNameMaxFc, -1);
            if(savedparMaxFc > 0) parMaxFc = savedparMaxFc;
        }
        
        FJLabel hSpaceLabel1 = new FJLabel("");
        GuiFactory.setSizes(hSpaceLabel1, new Dimension(fixedLabelWidth, hSpaceHeigth));
        FJLabel hSpaceLabel2 = new FJLabel("");
        GuiFactory.setSizes(hSpaceLabel2, new Dimension(fixedLabelWidth, hSpaceHeigth));
        FJLabel hSpaceLabel3 = new FJLabel("");
        GuiFactory.setSizes(hSpaceLabel3, new Dimension(fixedLabelWidth, hSpaceHeigth));
        FJLabel hSpaceLabel4 = new FJLabel("");
        GuiFactory.setSizes(hSpaceLabel4, new Dimension(fixedLabelWidth, hSpaceHeigth));
        FJLabel hSpaceLabel5 = new FJLabel("");
        GuiFactory.setSizes(hSpaceLabel5, new Dimension(fixedLabelWidth, hSpaceHeigth));
        
        FJLabel labelBinSize = new FJLabel(sLabelBinSize);
        FJLabel labelBinSizeHelp = new FJLabel(sBinSizeHelp);
        parBinSizeField = new RangedDoubleTextField(0.0, 1.0, new DoubleNumberFormatter());
        parBinSizeField.setDouble(parBinSize);
        parBinSizeField.setToolTipText(sBinSizeHelp);
        GuiFactory.setSizes(parBinSizeField, new Dimension(fixedFieldWidth, fixedFieldHeigth));
        GuiFactory.setSizes(labelBinSize, new Dimension(fixedLabelWidth, fixedLabelHeigth));
        HBox hboxBinSize = new HBox(new Component[] { labelBinSize, parBinSizeField });
        
        FJLabel labelCellCutoff = new FJLabel(sLabelCellCutoff);
        FJLabel labelCellCutoffHelp = new FJLabel(sCellCutoffHelp);
        parCellCutoffField = new RangedIntegerTextField(0, 2147483647);
        parCellCutoffField.setInt(parCellCutoff);
        parCellCutoffField.setToolTipText(sCellCutoffHelp);
        GuiFactory.setSizes(parCellCutoffField, new Dimension(fixedFieldWidth, fixedFieldHeigth));
        GuiFactory.setSizes(labelCellCutoff, new Dimension(fixedLabelWidth, fixedLabelHeigth));
        HBox hboxCellCutoff = new HBox(new Component[] { labelCellCutoff, parCellCutoffField });
        
        FJLabel labelCutoff = new FJLabel(sLabelCutoff);
        FJLabel labelCutoffHelp = new FJLabel(sCutoffHelp);
        parCutoffField = new RangedDoubleTextField(0.0, Float.MAX_VALUE, new DoubleNumberFormatter()); // Float.MAX_VALUE is enough although a doubles can hold more
        parCutoffField.setDouble(parCutoff);
        parCutoffField.setToolTipText(sCutoffHelp);
        GuiFactory.setSizes(parCutoffField, new Dimension(fixedFieldWidth, fixedFieldHeigth));
        GuiFactory.setSizes(labelCutoff, new Dimension(fixedLabelWidth, fixedLabelHeigth));
        HBox hboxCutoff = new HBox(new Component[] { labelCutoff, parCutoffField });

        FJLabel labelMaxFc = new FJLabel(sLabelMaxFc);
        FJLabel labelMaxFcHelp = new FJLabel(sMaxFcHelp);
        parMaxFcField = new RangedDoubleTextField(0.0, Float.MAX_VALUE, new DoubleNumberFormatter()); // Float.MAX_VALUE is enough although a doubles can hold more
        parMaxFcField.setDouble(parMaxFc);
        parMaxFcField.setToolTipText(sMaxFcHelp);
        GuiFactory.setSizes(parMaxFcField, new Dimension(fixedFieldWidth, fixedLabelHeigth));
        GuiFactory.setSizes(labelMaxFc, new Dimension(fixedLabelWidth, fixedLabelHeigth));
        HBox hboxMaxFc = new HBox(new Component[] { labelMaxFc, parMaxFcField });
        
        componentList.add(hSpaceLabel1);
        componentList.add(labelBinSizeHelp);
        componentList.add(hboxBinSize);
        
        componentList.add(hSpaceLabel2);
        componentList.add(labelCellCutoffHelp);
        componentList.add(hboxCellCutoff);
        
        componentList.add(hSpaceLabel3);
        componentList.add(labelCutoffHelp);
        componentList.add(hboxCutoff);     
        
        componentList.add(hSpaceLabel4);
        componentList.add(labelMaxFcHelp);
        componentList.add(hboxMaxFc);
        
        fShowOutputCheckBox = new JCheckBox("Save the R script and output messages");
        fShowOutputCheckBox.setToolTipText("Keep a file that shows execution of the script");
        fShowOutputCheckBox.setSelected(fShowOutput);
    	componentList.add(fShowOutputCheckBox);
    	
		if (!fRoutFile.isEmpty())
			componentList.add(new HBox(Box.createHorizontalGlue(),  createShowOutputButton(), Box.createHorizontalGlue()));

        componentList.add(hSpaceLabel5);
        componentList.add(new FJLabel(citingLabelLine1));
        componentList.add(new FJLabel(citingLabelLine2));
        componentList.add(new FJLabel(citingLabelLine3));
        componentList.add(new FJLabel(citingLabelLine4));
        
        return componentList;
    }
    
	@SuppressWarnings("deprecation")
	@Override
    protected void extractPromptOptions()
    {
    	fOptions = new HashMap<String, String>();
    	fParameterNames = new ArrayList<String>();

    	boolean timeParameterSelected = false;
    	// for (Object obj : fParameterNameList.getSelectedValuesList())
    	// FlowJo advised against using getSelectedValuesList() due to compatibility issues. Following the advise...
    	for (Object obj : fParameterNameList.getSelectedValues()) 
    	{
    		String parName = (new StringBuilder()).append("").append(obj).toString();
    		// FlowJo's parameter names are often in the form of Name :: Description, we only want the Name part from that
    		int parDescIndex = parName.indexOf(" :: ");
    		if(parDescIndex > 0) parName = parName.substring(0, parDescIndex);
    		if(parName.compareToIgnoreCase("Time") == 0) timeParameterSelected = true;
    		fParameterNames.add(parName);
    	}
    	// We really need the Time parameter, so we select it even if the user doesn't.
    	if(!timeParameterSelected) fParameterNames.add("Time");
    	
    	// Save all the flowClean specific options
        fOptions.put("BinSize", Double.toString(parBinSizeField.getDouble()));
        fOptions.put("CellCutoff", Double.toString(parCellCutoffField.getInt()));
        fOptions.put("Cutoff", Double.toString(parCutoffField.getDouble()));
        fOptions.put("MaxFc", Double.toString(parMaxFcField.getDouble()));

    	fShowOutput = fShowOutputCheckBox.isSelected();
    }

}
