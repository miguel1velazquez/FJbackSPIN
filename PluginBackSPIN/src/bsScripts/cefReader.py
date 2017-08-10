from Cef_tools import CEF_obj
import numpy as numpy

input_CEF = "BackSPIN_Output_CEF"
output_Clusters = "CLUSTERS_OUTFILE"
output_Gene_Sets = "GENE_SET_OUTFILE"

cef = CEF_obj()
cef.readCEF(input_CEF)
your_array = cef.matrix
attribute_values_list2 = cef.col_attr_names
attribute_values_list1 = cef.row_attr_values
rowvals_list = numpy.transpose(attribute_values_list1)

varListr = ['CellId','bRUNID']

numLevel = numpy.alen(attribute_values_list2)
covals_last = numpy.transpose(cef.col_attr_values[numLevel-1])
covals_gene = numpy.transpose(cef.col_attr_values[0])

covals = numpy.column_stack((covals_gene,covals_last))
covalsFinal = numpy.vstack((varListr, covals))

numpy.savetxt(output_Clusters, covalsFinal, delimiter=",",fmt="%s")
numpy.savetxt(output_Gene_Sets, rowvals_list, delimiter=",",fmt="%s")

# End writing clusters and gene sets.