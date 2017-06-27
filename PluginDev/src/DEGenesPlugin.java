package com.plugins.seqgeq;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
import com.flowjo.lib.parameters.ParameterSetMgrInterface;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.core.SeqGeqExternalAlgorithmResults;
import com.treestar.lib.fjml.types.FileTypes;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.numberfields.RangedIntegerTextField;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.xml.SElement;

/**
 * This class illustrates how to implement a SeqGeq population plugin, to show how to apply
 * a plugin node to multiple populations by retaining state when copied.
 * The plugin node is initially created on a 'source' population, at which time the plugin
 * calculates and stores the mean count value for each selected gene.  At this point,
 * the plugin is in 'sourceOnly' state.
 * When the plugin node is copied (by drag-n-drop) to another population, the new plugin node
 * uses the stored mean values from the source population to calculate the difference for each
 * gene in the target population.  If the diff is greater than the deltaMeanThreshold (entered
 * by the user in promptForOptions), then gene is added to a new gene set.
 */
public class DEGenesPlugin implements PopulationPluginInterface {
	private static final String gVersion = "1.0";
	private Map<String, Double> sourceParameterMeans = new HashMap<String, Double>(); // set in sourceOnly state
	private String sourceDataName = null; // used to uniquely name the new gene set
	private String sourceSuffix = null; // used to uniquely name the new gene set
	private double deltaMeanThreshold = 3d; // genes whose mean differ more than this are included in the new gene set
	private DEGenesPluginEnum pluginState = DEGenesPluginEnum.empty; // the initial plugin state

	// This enum defines the possible states of the plugin node
	public enum DEGenesPluginEnum 
	{
		empty, sourceOnly, sourceAndTarget
	}

	@Override
	public String getName() {
		return "DEGenesPlugin";
	}

	@Override
	public SElement getElement() {
		SElement result = new SElement(getName());
		// store the parameters and mean values the user selected
		if (!sourceParameterMeans.isEmpty())
		{
			SElement elem = new SElement("Parameters");
			result.addContent(elem);
			for (Map.Entry<String, Double> entry : sourceParameterMeans.entrySet())
			{
				SElement e = new SElement("P");
				e.setString("name", entry.getKey());
				e.setDouble("mean", entry.getValue());
				elem.addContent(e);
			}
		}
		result.setString("sourceDataName", sourceDataName);
		result.setString("sourceSuffix", sourceSuffix);
		result.setString("pluginState", pluginState.toString());
		result.setDouble("deltaMeanThreshold", deltaMeanThreshold);
		return result;
	}

	@Override
	public void setElement(SElement element) {
		SElement params = element.getChild("Parameters");
		if (params == null)
			return;
		sourceParameterMeans.clear();
		for (SElement elem : params.getChildren())
		{
			String name = elem.getString("name");
			Double value = elem.getDouble("mean");
			sourceParameterMeans.put(name, value);
		}
		sourceDataName = element.getString("sourceDataName");
		sourceSuffix = element.getString("sourceSuffix");
		pluginState = DEGenesPluginEnum.valueOf(element.getString("pluginState"));
		deltaMeanThreshold = element.getDouble("deltaMeanThreshold", deltaMeanThreshold);
	}

	/**
	 * This method returns a path string to the parent of the plugin node.
	 * @param fcmlQueryElement The xml element from the plugin api
	 * @return The gating path to the parent population
	 */
	private String getPath(SElement fcmlQueryElement)
	{
		List<String> paths = PluginHelper.getPathToPlugin(fcmlQueryElement);
		// the last path is the plugin node, so use it's parent gate name if it exists
		if (!paths.isEmpty())
			paths.remove(paths.size() - 1);
		return paths.isEmpty() ? "" : paths.get(paths.size() - 1);
	}

	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {

		SeqGeqExternalAlgorithmResults result = new SeqGeqExternalAlgorithmResults();

		if (pluginState == DEGenesPluginEnum.empty) // first creation on a population
		{
			sourceParameterMeans = getGeneAvgs(sampleFile);
			sourceDataName = sampleFile.getName();
			sourceSuffix = getPath(fcmlQueryElement);
			pluginState  = DEGenesPluginEnum.sourceOnly;
		}
		else if (pluginState == DEGenesPluginEnum.sourceOnly && !sourceDataName.equals(sampleFile.getName())) // copied to another different population
		{
			String suffix = getPath(fcmlQueryElement);
			determineDiffExpressions(sampleFile, result, suffix);
			pluginState  = DEGenesPluginEnum.sourceAndTarget;
		}

		// set the string displayed in the workspace based on the state
		switch (pluginState) {
		case empty:
			result.setWorkspaceString("None");
			break;
		case sourceOnly:
			result.setWorkspaceString("Source Population");
			break;
		case sourceAndTarget:
			result.setWorkspaceString("Diff'ed");
			break;
		default:
			break;
		}
		return result;
	}

	/**
	 * This method reads the input diff file, calculates the mean for each gene and determines
	 * if the difference with the source population's gene is greater than the deltaMeanThreshold.
	 * If so, the gene is added to a new, uniquely named gene set.
	 * @param diffFile The target population on which to calculate diffs
	 * @param result The algorithm results used to return a new gene set
	 * @param targetSuffix
	 */
	private void determineDiffExpressions(File diffFile, SeqGeqExternalAlgorithmResults result, String targetSuffix)
	{
		Map<String, Double> diffGeneMeans = getGeneAvgs(diffFile);
		List<String> diffGenes = new ArrayList<String>();
		// compare the mean of the src and target population's genes
		for (Map.Entry<String, Double> entry : sourceParameterMeans.entrySet())
		{
			Double val2 = diffGeneMeans.get(entry.getKey());
			if (val2 != null)
			{
				if (Math.abs(val2 - entry.getValue()) >= deltaMeanThreshold)
				{
					diffGenes.add(entry.getKey());
				}
			}
		}
		if (!diffGenes.isEmpty()) // found differential genes, create new gene set
		{
			String gsName = getUniqueGeneSetName(sourceDataName, diffFile.getName(), targetSuffix);
			ParameterSet pSet = new ParameterSet("DEG: " + gsName, diffGenes);
			result.addParameterSet(pSet);
		}
	}

	private String getUniqueGeneSetName(String srcName, String diffName, String suffix)
	{
		// strip out suffixes from source and target names
		int index = srcName.indexOf(FileTypes.CSV_SUFFIX);
		if (index < 0)
			index = srcName.indexOf(FileTypes.TSV_SUFFIX);
		if (index < 0)
			index = srcName.indexOf(FileTypes.TAB_SUFFIX);
		if (index > 0)
			srcName = srcName.substring(0,  index);

		index = diffName.indexOf(FileTypes.CSV_SUFFIX);
		if (index < 0)
			index = diffName.indexOf(FileTypes.TSV_SUFFIX);
		if (index < 0)
			index = diffName.indexOf(FileTypes.TAB_SUFFIX);
		if (index > 0)
			diffName = diffName.substring(0,  index);

		// if src and target are same, don't repeat the name
		if (srcName.contentEquals(diffName))
			return srcName + ":" + sourceSuffix + " vs. " + suffix;
		return srcName + ":" + sourceSuffix + " vs. " + diffName + ":" + suffix;
	}

	/**
	 * This method reads the input data file, parses each row as a series of
	 * count values for the gene identified in the first column, and updates
	 * a map of gene name to mean value.
	 * @param sampleFile The input data file, in paramsInRows format
	 * @return A map of gene names to their mean value
	 */
	private Map<String, Double> getGeneAvgs(File sampleFile)
	{
		Map<String, Double> result = new HashMap<String, Double>();
		BufferedReader sampleFileReader = null;
		try {
			sampleFileReader = new BufferedReader(new FileReader(sampleFile));

			String line = sampleFileReader.readLine(); // read header line, can ignore
			// now start reading data rows
			while ((line = sampleFileReader.readLine()) != null)
			{
				String gene = null;
				double total = 0;
				int ct = 0;
				StringTokenizer tokenizer = new StringTokenizer(line, ",");
				if (tokenizer.hasMoreTokens()) // first column is gene
				{
					gene = tokenizer.nextToken();
				}
				// remaining columns are gene counts
				while (tokenizer.hasMoreTokens())
				{
					// read gene count, convert to float, and increment cell and gene sums
					String valStr = tokenizer.nextToken();
					try {
						double val = Double.parseDouble(valStr);
						total += val;
						ct++;
					}
					catch (NumberFormatException e) { } // ignore for now
				}
				result.put(gene, total / ct);
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
		return result;
	}
	@Override
	public List<String> getParameters() {
		return new ArrayList<String>(sourceParameterMeans.keySet());
	}

	// Display a different icon depending upon the state of the plugin node
	private static Icon gIcon1, gIcon2 = null;
	@Override	public Icon getIcon()
	{
		if (gIcon1 == null) // first access, load the icons
		{
			URL url = getClass().getClassLoader().getResource("images/DE-sourceOnly-plugin-icon.png");
			if (url != null)
				gIcon1 = new ImageIcon(url);
			url = getClass().getClassLoader().getResource("images/DE-sourceAndTarget-plugin-icon.png");
			if (url != null)
				gIcon2 = new ImageIcon(url);
		}
		return pluginState == DEGenesPluginEnum.empty || pluginState == DEGenesPluginEnum.sourceOnly ? gIcon1 : gIcon2;
	}


	@Override
	public boolean promptForOptions(SElement fcmlQueryElement, List<String> pNames) {
		if (sourceDataName != null)
			return true;
		// Use a helper method to get a ParameterSetMgrInterface, used by ParameterSelectionPanel
		ParameterSetMgrInterface mgr = PluginHelper.getParameterSetMgr(fcmlQueryElement);
		if (mgr == null)
			return false;

		List<Object> guiObjects = new ArrayList<Object>();
		FJLabel explainText = new FJLabel();
		String text = "<html><body>";
		text += "This plugin compares the mean expression of each gene<br>";
		text += "in two populations, and generates a gene set containing<br>";
		text += "genes whose means differ by the threshold below.";
		text += "</body></html>";
		explainText.setText(text);
		guiObjects.add(explainText);

		FJLabel label = new FJLabel("Gene delta threshold: ");
		String tip = "Include genes whose means differ by this amount";
		label.setToolTipText(tip);
		RangedIntegerTextField deltaThresholdInputField = new RangedIntegerTextField(0, 30000);
    	deltaThresholdInputField.setDouble(deltaMeanThreshold);
    	deltaThresholdInputField.setToolTipText(tip);
    	GuiFactory.setSizes(deltaThresholdInputField, new Dimension(50, 25));
    	HBox box = new HBox(Box.createHorizontalGlue(), label, deltaThresholdInputField, Box.createHorizontalGlue());
    	guiObjects.add(box);
		
    	explainText = new FJLabel();
		text = "<html><body>";
		text += "Click OK and drag the new source population plugin node<br>";
		text += "to another population to create a new gene set";
		text += "</body></html>";
		explainText.setText(text);
		guiObjects.add(explainText);

		ParameterSelectionPanel pane = new ParameterSelectionPanel(mgr, eParameterSelectionMode.WithSetsAndParameters, true, false, false, true);
		Dimension dim = new Dimension(300, 500);
		pane.setMaximumSize(dim);
		pane.setMinimumSize(dim);
		pane.setPreferredSize(dim);

		pane.setSelectedParameters(sourceParameterMeans.keySet());
		guiObjects.add(pane);
		sourceParameterMeans.clear();

    	int option = JOptionPane.showConfirmDialog(null, guiObjects.toArray(), "DE Genes Plugin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null);
    	if (option == JOptionPane.OK_OPTION) {
    		// user clicked ok, get all selected parameters
    		for (String param : pane.getParameterSelection())
    		{
    			sourceParameterMeans.put(param, 0d);
    		}
    		deltaMeanThreshold = deltaThresholdInputField.getInt();
    		return true;
    	}
		return false;
	}

	// Since we're calculating the mean of each parameter, using paramInRows format
	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_PIR_SCALE;
	}

	@Override
	public String getVersion() {
		return gVersion;
	}

}
