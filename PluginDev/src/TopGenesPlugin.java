package src;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;

import com.flowjo.lib.parameters.ParameterSelectionPanel;
import com.flowjo.lib.parameters.ParameterSelectionPanel.eParameterSelectionMode;
import com.flowjo.lib.parameters.ParameterSet;
import com.flowjo.lib.parameters.ParameterSetMgrInterface;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.fjml.types.FileTypes;
import com.treestar.lib.gui.FontUtil;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.numberfields.RangedIntegerTextField;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.xml.SElement;

import corelib.src.com.treestar.lib.core.SeqGeqExternalAlgorithmResults;

/**
 * This class illustrates how to implement a SeqGeq population plugin, to show how to 
 * 1) read the various input file formats, 
 * 2) create new parameters by writing a mergeable CSV file, 
 * 3) define a new subpopulation using GatingML, 
 * 4) create a new gene set
 * 5) use ParameterSelectionPanel to select gene sets and genes
 */
public class TopGenesPlugin implements PopulationPluginInterface {

	private static final String gVersion = "1.0";
	private List<String> parameterNames = new ArrayList<String>();
	private ExportFileTypes fileType = ExportFileTypes.CSV_SCALE; // default, but user can change
	private int numGenesExpressedThreshold = 1000; // used to categorize a cell as below or above a threshold number of genes
	private float topGeneExpressionPercent = 0.40f; // used to determine which genes to put in the gene set
	private int totalAboveThreshold = 0;

	private static final String CATEGORY_PREFIX = ""; // make this non-empty to write a category (non-numeric) value (GatingML gate will not work then)
	private static final int ABOVE_VAL = 500;
	private static final int BELOW_VAL = 200;

	@Override
	public String getName() {
		return "TopGenesPlugin";
	}

	/* (non-Javadoc)
	 * @see com.treestar.lib.core.ExternalPopulationAlgorithmInterface#getElement()
	 * Save into XML all state needed in other methods
	 */
	@Override
	public SElement getElement() {
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
		result.setInt("geneCountThreshold", numGenesExpressedThreshold);
		result.setInt("totalAboveThreshold", totalAboveThreshold);
		result.setString("exportType", fileType.toString());
		result.setFloat("topGeneExpressionPercent", topGeneExpressionPercent);
		return result;
	}

	/* (non-Javadoc)
	 * @see com.treestar.lib.core.ExternalPopulationAlgorithmInterface#setElement(com.treestar.lib.xml.SElement)
	 * Restore state from XML.
	 */
	@Override
	public void setElement(SElement element) {
		SElement params = element.getChild("Parameters");
		if (params == null)
			return;
		parameterNames.clear();
		for (SElement elem : params.getChildren())
		{
			parameterNames.add(elem.getString("name"));
		}
		String exportType = element.getString("exportType");
		fileType = ExportFileTypes.valueOf(exportType);
		numGenesExpressedThreshold = element.getInt("geneCountThreshold", numGenesExpressedThreshold);
		totalAboveThreshold = element.getInt("totalAboveThreshold", totalAboveThreshold);
		topGeneExpressionPercent = element.getFloat("topGeneExpressionPercent", topGeneExpressionPercent);
	}

	@Override
	public SeqGeqExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		// create an SeqGeqExternalAlgorithmResults so we can return a gene set
		SeqGeqExternalAlgorithmResults result = new SeqGeqExternalAlgorithmResults();

		if (parameterNames.size() < 10)
		{
			result.setErrorMessage("Not enough genes were selected.");
			return result;
		}

		// use a helper method to get the gating path, so we can construct a unique suffix for parameter and gene set names
		List<String> paths = PluginHelper.getPathToPlugin(fcmlQueryElement);
		// the last path is the plugin node, so use it's parent gate name if it exists
		if (!paths.isEmpty())
			paths.remove(paths.size() - 1);
		String suffix = paths.isEmpty() ? "sample" : paths.get(paths.size() - 1);

		// construct a unique file name
		String fileName = getName() + "." + suffix + "." + sampleFile.getName();
		if (!fileName.endsWith(FileTypes.CSV_SUFFIX))
			fileName += FileTypes.CSV_SUFFIX;
		File outFile = new File(outputFolder, fileName);

		List<GeneCellSum> geneSums = null; // a sortable list of genes with their sums
		if (fileType == ExportFileTypes.CSV_SCALE)
			geneSums = writeParameterFileAndSumGenesParamsInCols(sampleFile, outFile, suffix);
		else if (fileType == ExportFileTypes.CSV_PIR_SCALE)
			geneSums = writeParameterFileAndSumGenesParamsInRows(sampleFile, outFile, suffix);
		else if (fileType == ExportFileTypes.TRIPLET_SCALE)
			geneSums = writeParameterFileAndSumGenesTriplets(sampleFile, outFile, suffix);

		if (outFile.exists())
			result.setCSVFile(outFile);
		String geneSetSuffix = getUniqueGeneSetName(sampleFile, suffix);
		addGeneSetsToResult(result, geneSetSuffix, geneSums, outputFolder);
		addGatingML(result, suffix);
		return result;
	}

	/**
	 * @param sampleFile The input data file
	 * @param suffix The gating paths
	 * @return A unique string with sample and population paths
	 */
	private String getUniqueGeneSetName(File sampleFile, String suffix)
	{
		String sampleName = sampleFile.getName();
		int index = sampleName.indexOf(FileTypes.CSV_SUFFIX);
		if (index < 0)
			index = sampleName.indexOf(FileTypes.TSV_SUFFIX);
		if (index < 0)
			index = sampleName.indexOf(FileTypes.TAB_SUFFIX);
		if (index > 0)
			sampleName = sampleName.substring(0,  index);
		if (!"sample".equals(suffix))
			sampleName += " " + suffix;
		return sampleName;
	}
	private String getSumGenesParameterName(String suffix)
	{
		return "Sum Genes " + suffix;
	}
	private String getNumGenesParameterName(String suffix)
	{
		return "Num Genes " + suffix;
	}
	private String getGenesThresholdParameterName(String suffix)
	{
		return "Num Genes Threshold " + suffix;
	}

	private void writeHeaderRow(String suffix, Writer output) throws IOException
	{
		// write header of output file (new parameters we will create)
		output.write("CellId,");
		output.write(getSumGenesParameterName(suffix));
		output.write(",");
		output.write(getNumGenesParameterName(suffix));
		output.write(",");
		output.write(getGenesThresholdParameterName(suffix));
		output.write("\n");
	}
	/**
	 * This method illustrates how to read an input data file in paramsInColumns format.
	 * @param sampleFile The input data file
	 * @param outFile The file to write
	 * @param suffix A unique for naming
	 * @return The list of gene sums to sort and use for gene set
	 */
	private List<GeneCellSum> writeParameterFileAndSumGenesParamsInCols(File sampleFile, File outFile, String suffix)
	{
		List<GeneCellSum> geneSums = new ArrayList<GeneCellSum>();
		Writer output;
		BufferedReader sampleFileReader;
		try {
			sampleFileReader = new BufferedReader(new FileReader(sampleFile));
			output = new BufferedWriter(new FileWriter(outFile));
			writeHeaderRow(suffix, output);

			String line = sampleFileReader.readLine(); // read header line
			StringTokenizer tokenizer = new StringTokenizer(line, ",");
			if (tokenizer.hasMoreTokens()) // first column is cell id
				tokenizer.nextToken();
			while (tokenizer.hasMoreTokens()) // remaining columns are gene names
			{
				String gene = tokenizer.nextToken();
				geneSums.add(new GeneCellSum(gene));
			}
			// now start reading data rows
			while ((line = sampleFileReader.readLine()) != null)
			{
				tokenizer = new StringTokenizer(line, ",");
				if (tokenizer.hasMoreTokens()) // first column is cell id, write it as first column
				{
					String cellId = tokenizer.nextToken();
					output.write(cellId);
				}
				float cellSum = 0;
				int numGenesExpressed = 0;
				int index = 0; // to find the GeneSum in the list
				// remaining columns are gene counts
				while (tokenizer.hasMoreTokens())
				{
					// read gene count, convert to float, and increment cell and gene sums
					String valStr = tokenizer.nextToken();
					try {
						float val = Float.parseFloat(valStr);
						cellSum += val;
						if (val > 0)
							numGenesExpressed++;
						if (index < geneSums.size())
							geneSums.get(index).increment(val);
					}
					catch (NumberFormatException e) { } // ignore for now
					index++;
				}
				// write the sum of all gene counts for this cell
				output.write("," + cellSum);
				output.write("," + numGenesExpressed);

				// write the categorical parameter value, above or below threshold
				boolean aboveThreshold = numGenesExpressed >= numGenesExpressedThreshold;
				String val = CATEGORY_PREFIX + (aboveThreshold ? ABOVE_VAL : BELOW_VAL);
				if (aboveThreshold)
					totalAboveThreshold++;
				output.write("," + val);
				output.write("\n");
			}
			sampleFileReader.close();
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return geneSums;
	}

	/**
	 * This method illustrates how to read an input data file in paramsInRows format.
	 * @param sampleFile The input data file
	 * @param outFile The file to write
	 * @param suffix A unique for naming
	 * @return The list of gene sums to sort and use for gene set
	 */
	private List<GeneCellSum> writeParameterFileAndSumGenesParamsInRows(File sampleFile, File outFile, String suffix)
	{
		List<GeneCellSum> geneSums = new ArrayList<GeneCellSum>();
		List<GeneCellSum> cellSums = new ArrayList<GeneCellSum>();
		BufferedReader sampleFileReader = null;
		try {
			sampleFileReader = new BufferedReader(new FileReader(sampleFile));

			String line = sampleFileReader.readLine(); // read header line
			StringTokenizer tokenizer = new StringTokenizer(line, ",");
			if (tokenizer.hasMoreTokens()) // first column is not a cell id
				tokenizer.nextToken();
			while (tokenizer.hasMoreTokens()) // remaining columns are cell id's
			{
				String cell = tokenizer.nextToken();
				cellSums.add(new GeneCellSum(cell));
			}
			// now start reading data rows
			while ((line = sampleFileReader.readLine()) != null)
			{
				tokenizer = new StringTokenizer(line, ",");
				GeneCellSum geneSum = null;
				if (tokenizer.hasMoreTokens()) // first column is gene
				{
					String gene = tokenizer.nextToken();
					geneSum = new GeneCellSum(gene);
					geneSums.add(geneSum);
				}
				else
					return geneSums;
				int index = 0; // to find the GeneSum in the list
				// remaining columns are gene counts
				while (tokenizer.hasMoreTokens())
				{
					// read gene count, convert to float, and increment cell and gene sums
					String valStr = tokenizer.nextToken();
					try {
						float val = Float.parseFloat(valStr);
						if (index < cellSums.size())
							cellSums.get(index).increment(val);
						geneSum.increment(val);
					}
					catch (NumberFormatException e) { } // ignore for now
					index++;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally
		{
			if (sampleFileReader != null)
				try {
					sampleFileReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		Writer output;
		try {
			output = new BufferedWriter(new FileWriter(outFile));
			writeHeaderRow(suffix, output);
			for (GeneCellSum cellSum : cellSums)
			{
				output.write(cellSum.name + "," + cellSum.sum + "," + cellSum.numAboveThreshold);
				// write the categorical parameter value, above or below threshold
				boolean aboveThreshold = cellSum.numAboveThreshold >= numGenesExpressedThreshold;
				String val = CATEGORY_PREFIX + (aboveThreshold ? ABOVE_VAL : BELOW_VAL);
				if (aboveThreshold)
					totalAboveThreshold++;
				output.write("," + val);
				output.write("\n");
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return geneSums;
	}

	/**
	 * This method illustrates how to read an input data file in triplet format.
	 * @param sampleFile The input data file
	 * @param outFile The file to write
	 * @param suffix A unique for naming
	 * @return The list of gene sums to sort and use for gene set
	 */
	private List<GeneCellSum> writeParameterFileAndSumGenesTriplets(File sampleFile, File outFile, String suffix)
	{
		List<GeneCellSum> geneSums = new ArrayList<GeneCellSum>();
		Map<String, GeneCellSum> geneMap = new HashMap<String, GeneCellSum>();
		Map<String, GeneCellSum> cellMap = new HashMap<String, GeneCellSum>();
		BufferedReader sampleFileReader = null;
		try {
			sampleFileReader = new BufferedReader(new FileReader(sampleFile));

			String line = sampleFileReader.readLine(); // read header line
			// now start reading data rows
			GeneCellSum prevGeneSum = null;
			while ((line = sampleFileReader.readLine()) != null)
			{
				String cellId = null;
				String geneName = null;
				GeneCellSum cellSum;
				GeneCellSum geneSum;
				StringTokenizer tokenizer = new StringTokenizer(line, ",");
				if (tokenizer.hasMoreTokens()) // first column is cell id
				{
					cellId = tokenizer.nextToken();
					cellSum = cellMap.get(cellId);
					if (cellSum == null)
					{
						cellSum = new GeneCellSum(cellId);
						cellMap.put(cellId, cellSum);
					}						
				}
				else
					return geneSums;
				if (tokenizer.hasMoreTokens()) // second column is gene id
				{
					geneName = tokenizer.nextToken();
					if (prevGeneSum != null && prevGeneSum.name.equals(geneName))
						geneSum = prevGeneSum;
					else
					{
						geneSum = geneMap.get(geneName);
						if (geneSum == null)
						{
							geneSum = new GeneCellSum(geneName);
							geneMap.put(geneName, geneSum);
						}						
					}
				}
				else
					return geneSums;
				if (tokenizer.hasMoreTokens()) // third column is gene count
				{
					// read gene count, convert to float, and increment cell and gene sums
					String valStr = tokenizer.nextToken();
					try {
						float val = Float.parseFloat(valStr);
						cellSum.increment(val);
						geneSum.increment(val);
					}
					catch (NumberFormatException e) { } // ignore for now
				}
				prevGeneSum = geneSum;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		finally
		{
			if (sampleFileReader != null)
				try {
					sampleFileReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		geneSums.addAll(geneMap.values());
		Writer output;
		try {
			output = new BufferedWriter(new FileWriter(outFile));
			writeHeaderRow(suffix, output);
			for (GeneCellSum cellSum : cellMap.values())
			{
				output.write(cellSum.name + "," + cellSum.sum + "," + cellSum.numAboveThreshold);
				// write the categorical parameter value, above or below threshold
				boolean aboveThreshold = cellSum.numAboveThreshold >= numGenesExpressedThreshold;
				String val = CATEGORY_PREFIX + (aboveThreshold ? ABOVE_VAL : BELOW_VAL);
				if (aboveThreshold)
					totalAboveThreshold++;
				output.write("," + val);
				output.write("\n");
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return geneSums;
	}

	/**
	 * This method creates a new gene set by sorting the gene sums and adding the ones with the highest sums
	 * until the percentage to include is met.  Once the percentage is met, continue to include genes with the
	 * exact same sum until the next one differs.
	 * @param result The result to return to the plugin framework
	 * @param suffix The path string to help construct a unique name
	 * @param geneSums A list of genes with their sums
	 */
	private void addGeneSetsToResult(SeqGeqExternalAlgorithmResults result, String suffix, List<GeneCellSum> geneSums, File outputFolder)
	{
		// convert the list to an array so we can sort
		GeneCellSum[] geneSumsArray = new GeneCellSum[geneSums.size()];
		Arrays.sort(geneSums.toArray(geneSumsArray));
		List<String> genes = new ArrayList<String>();
		float total = 0;
		for (int i = 0; i < geneSumsArray.length; i++)
			total += geneSumsArray[i].sum;

		float cumulative = 0;
		float prevSum = Float.NaN;
		boolean breakIfPrevDifferent = false;
		int i = 0;
		for (i = geneSumsArray.length-1; i >= 0; i--)
		{
			// we've reached the percentage, check if current value is different than last
			if (breakIfPrevDifferent && prevSum != geneSumsArray[i].sum)
				break;
			cumulative += geneSumsArray[i].sum;
			if (cumulative / total > topGeneExpressionPercent)
			{
				if (prevSum != geneSumsArray[i].sum) // if this value different than last, we can break now
					break;
				breakIfPrevDifferent = true;
			}
			String name = geneSumsArray[i].name;
			if (name.startsWith("\"") && name.endsWith("\""))
				name = name.substring(1, name.length()-1);
			genes.add(name);
			prevSum = geneSumsArray[i].sum;
		}
		ParameterSet pSet = new ParameterSet("Top Genes - " + suffix, genes);
		result.addParameterSet(pSet);
		File imageFile = writeSortedGenesImageFile(geneSumsArray, outputFolder, suffix, i, pSet);
		if (imageFile.exists())
		{
			try {
				result.setImageURL(imageFile.toURI().toURL());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
	}

	private File writeSortedGenesImageFile(GeneCellSum[] geneSumsArray, File outputFolder, String suffix, int index, ParameterSet pSet)
	{
		int imageWidth = 500;
		int imageHeight = 400;
		float maxGeneSum = geneSumsArray[geneSumsArray.length-1].sum;

		BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = (Graphics2D)image.getGraphics();
		graphics.setColor(Color.white); // first fill rectangle with white
		graphics.fillRect(0, 0, imageWidth, imageHeight);

		graphics.setColor(Color.cyan); // highlight the selected genes with color
		float decr = (float)geneSumsArray.length / (float)imageWidth;
		int width = 2;
		int x = imageWidth;
		for (int i = geneSumsArray.length - 1; i >= 0; i -= decr)
		{
			int height = (int)((geneSumsArray[i].sum / maxGeneSum) * imageHeight);
			graphics.fillRect(x, imageHeight - height, width, imageHeight);
			if (i <= index) // no longer highlight
				graphics.setColor(Color.blue);
			x--;
		}
		// draw bounding rectangle
		graphics.setColor(Color.black);
		graphics.drawRect(0, 0, imageWidth-1, imageHeight-1);

		// create outer image to include text
		imageHeight += 50; // 25 at the top and 25 at the bottom
		BufferedImage outerImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
		graphics = (Graphics2D)outerImage.getGraphics();
		graphics.setColor(Color.white);
		graphics.fillRect(0, 0, imageWidth, imageHeight);
		graphics.drawImage(image, 0, 25, null);

		graphics.setColor(Color.black);
		graphics.setFont(FontUtil.dlogBold16);
		graphics.drawString(pSet.getName(), 10, 20);

		graphics.setFont(FontUtil.BoldDialog12);
		String msg = "Total number of genes: " +  geneSumsArray.length + ", Number of genes in top " + (int)(topGeneExpressionPercent * 100) + "%: " + pSet.getParameterNames().size();
		graphics.drawString(msg, 10, imageHeight - 4);

		File outputFile = new File(outputFolder, suffix + ".png");
	    try {
			ImageIO.write(outerImage, "png", outputFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	    return outputFile;
	}

	@Override
	public List<String> getParameters() {
		return parameterNames;	
	}

	private static Icon gIcon = null;
	@Override	public Icon getIcon()
	{
		if (gIcon == null)
		{
			URL url = getClass().getClassLoader().getResource("images/plugin-icon.png");
			if (url != null)
				gIcon = new ImageIcon(url);
		}
		return gIcon;
	}

	/* (non-Javadoc)
	 * @see com.treestar.lib.core.ExternalPopulationAlgorithmInterface#promptForOptions(com.treestar.lib.xml.SElement, java.util.List)
	 * Construct a dialog to prompt the user for the parameters, the format of the input file, and the gene sum threshold to use to create a categorical parameter.
	 */
	@Override
	public boolean promptForOptions(SElement fcmlQueryElement, List<String> pNames) {
		// Use a helper method to get a ParameterSetMgrInterface, used by ParameterSelectionPanel
		ParameterSetMgrInterface mgr = PluginHelper.getParameterSetMgr(fcmlQueryElement);
		if (mgr == null)
			return false;

		List<Object> guiObjects = new ArrayList<Object>();
		FJLabel explainText = new FJLabel();
		guiObjects.add(explainText);
		String text = "<html><body>";
		text += "This plugin creates 3 new parameters:";
		text += "<ul>";
		text += "<li>the total count of all genes expressed per cell</li>";
		text += "<li>the number of genes expressed per cell</li>";
		text += "<li>whether the cell expresses above a threshold number of genes</li>";
		text += "</ul>";
		text += "</body></html>";
		explainText.setText(text);

    	explainText = new FJLabel();
		guiObjects.add(explainText);
		text = "<html><body>";
		text += "Using the threshold parameter, create a new gate (using GatingML) <br>to select the cells above the threshold.";
		text += "</body></html>";
		explainText.setText(text);

		FJLabel label = new FJLabel("Threshold number of genes expressed:");
		String tip = "Create subpopulation of cells based on number of genes expressed";
		label.setToolTipText(tip);
		RangedIntegerTextField countThresholdInputField = new RangedIntegerTextField(0, 30000);
    	countThresholdInputField.setInt(numGenesExpressedThreshold);
    	countThresholdInputField.setToolTipText(tip);
    	GuiFactory.setSizes(countThresholdInputField, new Dimension(50, 25));
    	HBox box = new HBox(Box.createHorizontalGlue(), label, countThresholdInputField, Box.createHorizontalGlue());
    	guiObjects.add(box);

    	explainText = new FJLabel();
		guiObjects.add(explainText);
		text = "<html><body>";
		text += "This plugin also creates a gene set containing most expressed genes.";
		text += "</body></html>";
		explainText.setText(text);

		label = new FJLabel("Percentage of genes to create gene set:(1-90):");
		tip = "Sort genes by highest, then add to gene set until this percentage is met";
		label.setToolTipText(tip);
		RangedIntegerTextField topGeneExpressionPercentInputField = new RangedIntegerTextField(1, 90);
		topGeneExpressionPercentInputField.setInt((int)(topGeneExpressionPercent * 100));
    	topGeneExpressionPercentInputField.setToolTipText(tip);
    	GuiFactory.setSizes(topGeneExpressionPercentInputField, new Dimension(50, 25));
    	box = new HBox(Box.createHorizontalGlue(), label, topGeneExpressionPercentInputField, Box.createHorizontalGlue());
    	guiObjects.add(box);

		ParameterSelectionPanel pane = new ParameterSelectionPanel(mgr, eParameterSelectionMode.WithSetsAndParameters, true, false, false, true);
		Dimension dim = new Dimension(300, 500);
		pane.setMaximumSize(dim);
		pane.setMinimumSize(dim);
		pane.setPreferredSize(dim);

		pane.setSelectedParameters(parameterNames);
		parameterNames.clear();

		ExportFileTypes[] fileTypes = { ExportFileTypes.CSV_SCALE, ExportFileTypes.CSV_PIR_SCALE, ExportFileTypes.TRIPLET_SCALE };
		guiObjects.add(pane);

    	tip = "Should get the same results regardless of format.";
    	FJLabel label2 = new FJLabel("Choose file format as input to plugin:");
		label2.setToolTipText(tip);
		guiObjects.add(label2);
		ButtonGroup bGroup = new ButtonGroup();
		// build radio buttons for each kind of file format
		for (ExportFileTypes type : fileTypes)
		{
			JRadioButton button = new JRadioButton(type.toString());
			button.setActionCommand(type.toString());
			bGroup.add(button);
			guiObjects.add(button);
			if (type == fileType)
				button.setSelected(true);
			if (type == ExportFileTypes.CSV_SCALE)
				button.setToolTipText("Input file contains the genes in columns");
			else if (type == ExportFileTypes.CSV_PIR_SCALE)
				button.setToolTipText("Input file contains the genes in rows");
			else if (type == ExportFileTypes.TRIPLET_SCALE)
				button.setToolTipText("Input file consists of rows of cellId, geneId, count");
		}
    	int option = JOptionPane.showConfirmDialog(null, guiObjects.toArray(), "Top Genes Plugin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null);
    	if (option == JOptionPane.OK_OPTION) {
    		// user clicked ok, get all selected parameters
    		parameterNames.addAll(pane.getParameterSelection());
    		// make sure 'CellId' is included, so input data file will have it
    		if (!parameterNames.contains("CellId"))
    			parameterNames.add("CellId");
    		// get other GUI inputs
    		fileType = ExportFileTypes.valueOf(bGroup.getSelection().getActionCommand());
    		numGenesExpressedThreshold = countThresholdInputField.getInt();
    		topGeneExpressionPercent = (float)topGeneExpressionPercentInputField.getInt() / 100f;
    		return true;
    	}
		return false;
	}

	@Override
	public ExportFileTypes useExportFileType() {		return fileType; }
	@Override
	public String getVersion() {	return gVersion;	}

	// This class wraps a gene name and its sum value and a count of times its count is above a threshold, 
	// and implements Comparable so it can be sorted by sum
	private class GeneCellSum implements Comparable<GeneCellSum>
	{
		protected String name;
		protected float sum = 0;
		protected int numAboveThreshold = 0;
		protected float threshold = 0;
		public GeneCellSum(String n)
		{
			name = n;
		}
		@Override
		public int compareTo(GeneCellSum o) {
			return Float.compare(sum, o.sum);
		}
		public void increment(float v)
		{
			sum += v;
			if (v > threshold)
				numAboveThreshold++;
		}
		public String toString()
		{
			return name + " (" + sum + ")";
		}
	}

	private void addGatingML(ExternalAlgorithmResults result, String suffix)
	{
		// create the XML elements for a 1-D range gate
		SElement gate = new SElement("gating:Gating-ML");
		SElement rectGateElem = new SElement("gating:RectangleGate");
		rectGateElem.setString("gating:id", "Above Threshold");
		gate.addContent(rectGateElem);
		// create the dimension XML element
		SElement dimElem = new SElement("gating:dimension");
		dimElem.setInt("gating:min", ABOVE_VAL - 20);
		dimElem.setInt("gating:max", ABOVE_VAL + 20);
		rectGateElem.addContent(dimElem);
		// create the parameter name XML element
		SElement fcsDimElem = new SElement("data-type:fcs-dimension");
		fcsDimElem.setString("data-type:name", getGenesThresholdParameterName(suffix));
		dimElem.addContent(fcsDimElem);

		// 10. Set the Gating-ML element in the result
		result.setGatingML(gate.toString());
	}
}
