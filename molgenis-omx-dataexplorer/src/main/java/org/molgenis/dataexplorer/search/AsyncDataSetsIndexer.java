package org.molgenis.dataexplorer.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.molgenis.framework.db.Database;
import org.molgenis.framework.db.DatabaseException;
import org.molgenis.framework.tupletable.TableException;
import org.molgenis.omx.dataset.DataSetTable;
import org.molgenis.omx.observ.DataSet;
import org.molgenis.omx.protocol.ProtocolTable;
import org.molgenis.search.SearchService;
import org.molgenis.util.DatabaseUtil;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

/**
 * Class that indexes all datasets for use in the dataexplorer async
 * 
 * @author erwin
 * 
 */
public class AsyncDataSetsIndexer implements DataSetsIndexer, InitializingBean
{
	private static final Logger LOG = Logger.getLogger(AsyncDataSetsIndexer.class);

	private SearchService searchService;
	private final AtomicInteger runningIndexProcesses = new AtomicInteger();

	@Autowired
	public void setSearchService(SearchService searchService)
	{
		this.searchService = searchService;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		if (searchService == null) throw new IllegalArgumentException("Missing bean of type SearchService");
	}

	@Override
	public boolean isIndexingRunning()
	{
		return (runningIndexProcesses.get() > 0);
	}

	/**
	 * Index all datasets
	 * 
	 * @throws DatabaseException
	 * @throws TableException
	 */
	@Override
	@Async
	public void index()
	{
		runningIndexProcesses.incrementAndGet();
		Database unauthorizedDatabase = DatabaseUtil.createDatabase();
		try
		{
			for (DataSet dataSet : unauthorizedDatabase.find(DataSet.class))
			{
				searchService.indexTupleTable(dataSet.getIdentifier(), new DataSetTable(dataSet, unauthorizedDatabase));
				searchService.indexTupleTable("protocolTree-" + dataSet.getId(),
						new ProtocolTable(dataSet.getProtocolUsed(), unauthorizedDatabase));
			}
		}
		catch (Exception e)
		{
			LOG.error("Exception index()", e);
		}
		finally
		{
			DatabaseUtil.closeQuietly(unauthorizedDatabase);
			runningIndexProcesses.decrementAndGet();
		}
	}

	/**
	 * Index all datatsets that are not in the index yet
	 * 
	 * @throws DatabaseException
	 * @throws TableException
	 */
	@Override
	@Async
	public void indexNew()
	{
		List<Integer> dataSetIds = new ArrayList<Integer>();

		Database unauthorizedDatabase = DatabaseUtil.createDatabase();
		try
		{
			for (DataSet dataSet : unauthorizedDatabase.find(DataSet.class))
			{
				if (!searchService.documentTypeExists(dataSet.getIdentifier()))
				{
					dataSetIds.add(dataSet.getId());
				}
			}
		}
		catch (Exception e)
		{
			LOG.error("Exception index()", e);
		}
		finally
		{
			DatabaseUtil.closeQuietly(unauthorizedDatabase);
		}

		if (!dataSetIds.isEmpty())
		{
			index(dataSetIds);
		}
	}

	@Override
	@Async
	public void index(List<Integer> dataSetIds)
	{
		while (isIndexingRunning())
		{
			try
			{
				Thread.sleep(5000);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}

		runningIndexProcesses.incrementAndGet();
		Database unauthorizedDatabase = DatabaseUtil.createDatabase();
		try
		{
			List<DataSet> dataSets = unauthorizedDatabase.query(DataSet.class).in(DataSet.ID, dataSetIds).find();

			for (DataSet dataSet : dataSets)
			{
				searchService.indexTupleTable(dataSet.getIdentifier(), new DataSetTable(dataSet, unauthorizedDatabase));
				searchService.indexTupleTable("protocolTree-" + dataSet.getId(),
						new ProtocolTable(dataSet.getProtocolUsed(), unauthorizedDatabase));
			}
		}
		catch (Exception e)
		{
			LOG.error("Exception index()", e);
		}
		finally
		{
			DatabaseUtil.closeQuietly(unauthorizedDatabase);
			runningIndexProcesses.decrementAndGet();
		}
	}

}
