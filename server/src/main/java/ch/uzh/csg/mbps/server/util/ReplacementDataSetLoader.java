package ch.uzh.csg.mbps.server.util;

import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ReplacementDataSet;

import com.github.springtestdbunit.dataset.DataSetLoader;
import com.github.springtestdbunit.dataset.FlatXmlDataSetLoader;

//, dataSetLoader = ReplacementDataSetLoader.class

/**
 * A data set loader that can be used to load
 * xml datasets, replacing "[null]" with <code>null</code>.
 * 
 * @author Stijn Van Bael
 * com.github.springtestdbunit.dataset
 */
public class ReplacementDataSetLoader implements DataSetLoader {
	private DataSetLoader delegate;

	public ReplacementDataSetLoader() {
		this(new FlatXmlDataSetLoader());
	}

	public ReplacementDataSetLoader(DataSetLoader delegate) {
		this.delegate = delegate;
	}

	public IDataSet loadDataSet(Class<?> testClass, String location)
			throws Exception {
		ReplacementDataSet replacementDataSet = new ReplacementDataSet(
				delegate.loadDataSet(testClass, location));
		replacementDataSet.addReplacementObject("[null]", null);
		return replacementDataSet;
	}
}