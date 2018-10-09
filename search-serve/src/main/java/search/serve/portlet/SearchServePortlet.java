package search.serve.portlet;

import com.liferay.asset.kernel.AssetRendererFactoryRegistryUtil;
import com.liferay.asset.kernel.model.AssetRenderer;
import com.liferay.asset.kernel.model.AssetRendererFactory;
import com.liferay.portal.kernel.configuration.Configuration;
import com.liferay.portal.kernel.configuration.ConfigurationFactoryUtil;
import com.liferay.portal.kernel.dao.orm.Criterion;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.model.LayoutConstants;
import com.liferay.portal.kernel.portlet.PortletPreferencesFactoryUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.search.Document;
import com.liferay.portal.kernel.search.Field;
import com.liferay.portal.kernel.search.Hits;
import com.liferay.portal.kernel.search.SearchContext;
import com.liferay.portal.kernel.search.SearchContextFactory;
import com.liferay.portal.kernel.search.SearchException;
import com.liferay.portal.kernel.search.facet.AssetEntriesFacet;
import com.liferay.portal.kernel.search.facet.Facet;
import com.liferay.portal.kernel.search.facet.ScopeFacet;
import com.liferay.portal.kernel.search.facet.faceted.searcher.FacetedSearcher;
import com.liferay.portal.kernel.search.facet.faceted.searcher.FacetedSearcherManagerUtil;
import com.liferay.portal.kernel.security.permission.comparator.ModelResourceComparator;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ListUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.PredicateFilter;
import com.liferay.portal.kernel.util.SortedArrayList;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.search.web.facet.SearchFacet;
import com.liferay.portal.search.web.facet.util.SearchFacetTracker;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.portlet.WindowState;

import org.osgi.service.component.annotations.Component;

import search.serve.constants.SearchServePortletKeys;

/**
 * @author nisarg
 */
@Component(immediate = true, property = { "com.liferay.portlet.display-category=category.sample",
		"com.liferay.portlet.instanceable=true", "javax.portlet.display-name=search-serve Portlet",
		"javax.portlet.init-param.template-path=/", "javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + SearchServePortletKeys.SearchServe, "javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=power-user,user" }, service = Portlet.class)
public class SearchServePortlet extends MVCPortlet {

	private static Log _log = LogFactoryUtil.getLog(SearchServePortlet.class);

	public void serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse)
			throws PortletException {

		_log.info("Calling Serve with keyword:" + ParamUtil.getString(resourceRequest, "keywordsPass"));

		String keywords = ParamUtil.getString(resourceRequest, "keywordsPass");
		if (Validator.isNull(keywords)) {
			return;
		}

		//get portlet preferences
		try {
			_portletPreferences = PortletPreferencesFactoryUtil.getPortletPreferences(
					PortalUtil.getHttpServletRequest(resourceRequest),
					"com_liferay_portal_search_web_portlet_SearchPortlet");
		} catch (Exception e1) {
			_log.error("Error in getting portlet preferences:",e1);
		}

		JSONObject jsonObject = null;
		JSONArray results = JSONFactoryUtil.createJSONArray();
		
		ThemeDisplay themeDisplay = (ThemeDisplay) resourceRequest.getAttribute(WebKeys.THEME_DISPLAY);

		//set search context
		FacetedSearcher facetedSearcher = FacetedSearcherManagerUtil.getFacetedSearcherManager()
				.createFacetedSearcher();

		SearchContext searchContext = SearchContextFactory
				.getInstance(PortalUtil.getHttpServletRequest(resourceRequest));

		//searchContext.setQueryConfig(getQueryConfig());
		
		//sest and enable facets
		addAssetEntriesFacet(searchContext);

		addScopeFacet(searchContext);
		
		searchContext.setKeywords(keywords);

		try {
			addEnabledSearchFacets(themeDisplay.getCompanyId(), searchContext);
		} catch (Exception e1) {
			_log.error("Error in enabling search facets:",e1);
		}

		try {
			//get restuls
			Hits hits = facetedSearcher.search(searchContext);
			_log.info("search result length:" + hits.getLength());
			
			//get portlet properties
			Configuration configuration = ConfigurationFactoryUtil.getConfiguration(PortalClassLoaderUtil.getClassLoader(), "portlet");
			int length = GetterUtil.getInteger(configuration.get("auto.search.suggest.result.length"));
			
			//get asset types and create array for all
			String[] values = getAssetTypes(themeDisplay.getCompanyId());
			List<String> assetTypes = new SortedArrayList<String>(new ModelResourceComparator(themeDisplay.getLocale()));

			for (String className : values) {
				if (assetTypes.contains(className) || !ArrayUtil.contains(values, className)) {
					continue;
				}
				assetTypes.add(className);
			}
			
			//create json array for each asset
			Map<String, JSONArray> assetEntries = new HashMap<String, JSONArray>();
			JSONArray assetTypeArray = null;
			for(String assetType : assetTypes){
				assetTypeArray = JSONFactoryUtil.createJSONArray();
				AssetRendererFactory<?> assetRendererFactory = AssetRendererFactoryRegistryUtil.getAssetRendererFactoryByClassName(assetType);
				//remove users from search
				if(assetRendererFactory.getTypeName(themeDisplay.getLocale()).equalsIgnoreCase("User")){
					continue;
				}
				assetEntries.put(assetRendererFactory.getTypeName(themeDisplay.getLocale()), assetTypeArray);
			}
			//add page layout array explicitely
			assetTypeArray = JSONFactoryUtil.createJSONArray();
			assetEntries.put("Page", assetTypeArray);
			
			//run through the result and get the title and view url
			long hitLength = hits.getLength();
			for (int i = 0; i < hitLength ; i++) {
				Document doc = hits.doc(i);
				String className = doc.get(Field.ENTRY_CLASS_NAME);
				long classPK = GetterUtil.getLong(doc.get(Field.ENTRY_CLASS_PK));
				//_log.info("classname::classpk::" + className + "-" + classPK);
				
				AssetRendererFactory<?> assetRendererFactory = AssetRendererFactoryRegistryUtil.getAssetRendererFactoryByClassName(className);
				AssetRenderer<?> assetRenderer = assetRendererFactory.getAssetRenderer(classPK);
				
				JSONArray assetEntry = assetEntries.get(assetRendererFactory.getTypeName(themeDisplay.getLocale()));
				if(Validator.isNull(assetEntry)){
					continue;
				}
				if(assetEntry.length() == length){
					continue;
				}
				
				String assetTitle = assetRenderer.getTitle(themeDisplay.getLocale());
				String assetViewUrl = assetRenderer.getURLView(PortalUtil.getLiferayPortletResponse(resourceResponse), WindowState.MAXIMIZED); 
				
				jsonObject = JSONFactoryUtil.createJSONObject();
				jsonObject.put("Title", StringUtil.shorten(assetTitle, titleLength));
				jsonObject.put("ViewURL", assetViewUrl);
				
				
				assetEntry.put(jsonObject);
			}
			
			//Add layout search result
			DynamicQuery dq = DynamicQueryFactoryUtil
					.forClass(Layout.class);
			Criterion companyCriterion = RestrictionsFactoryUtil.eq("groupId",
					themeDisplay.getScopeGroupId());
			_log.info("Group Id:"+themeDisplay.getScopeGroupId());
			//match regext - %language-id="%">%Z%</Name>% - page layout has xml format in name column
			String match = StringPool.PERCENT + "language-id=%>" + StringPool.PERCENT + keywords + StringPool.PERCENT + "</Name>%"; 
			Criterion titleLike = RestrictionsFactoryUtil.ilike("name", match);
			
			companyCriterion = RestrictionsFactoryUtil.and(companyCriterion,
					titleLike);
			
			//remove this criteria if all type of page needs to be search
			Criterion typeCriterion = RestrictionsFactoryUtil.eq("type",LayoutConstants.TYPE_URL);
			companyCriterion = RestrictionsFactoryUtil.and(companyCriterion,
					typeCriterion);
			
			//remove hidden pages
			Criterion hiddenCriterion = RestrictionsFactoryUtil.eq("hidden",Boolean.FALSE);
			companyCriterion = RestrictionsFactoryUtil.and(companyCriterion,
					hiddenCriterion);
			
			dq.add(companyCriterion);
			List<Layout> layoutList = LayoutLocalServiceUtil.dynamicQuery(dq);
			_log.info("Layout search result:"+layoutList.size());
			if(Validator.isNotNull(layoutList) && !layoutList.isEmpty() ){
				for(Layout layout : layoutList){
					jsonObject = JSONFactoryUtil.createJSONObject();
					jsonObject.put("Title", layout.getName(themeDisplay.getLocale()));
					String pageUrl = Validator.isNull(layout.getTypeSettingsProperty(LayoutConstants.TYPE_URL)) ? layout.getFriendlyURL() : layout.getTypeSettingsProperty(LayoutConstants.TYPE_URL);
					jsonObject.put("ViewURL", pageUrl);
					
					JSONArray assetEntry = assetEntries.get("Page");
					if(assetEntry.length() == length){
						continue;
					}
					assetEntry.put(jsonObject);					
				}
			}
			
			if((Validator.isNull(hits) || hits.getLength()==0) 
					&& (Validator.isNull(layoutList) || layoutList.size()==0)) {
				results.put("noresult");
			}
			else {
				results.put(assetEntries);
			}
			
			
		} catch (SearchException e1) {
			_log.error("Error in search:"+e1);
		} catch (PortalException e) {
			_log.error("Portal Exception:",e);
		} catch (Exception e) {
			_log.error("Error in getting search result:",e);
		}

		//send result back to UI
		PrintWriter out = null;
		try {
			out = resourceResponse.getWriter();
		} catch (IOException e) {
			_log.error("Error in IO operation:",e);
		}
		out.println(results.toString());
		_log.info("Search result:" + results.toString());

	}
	
	protected String[] getAssetTypes(long companyId) {
		List<String> assetTypes = new ArrayList<>();

		List<AssetRendererFactory<?>> assetRendererFactories =
			AssetRendererFactoryRegistryUtil.getAssetRendererFactories(
				companyId);

		for (int i = 0; i < assetRendererFactories.size(); i++) {
			AssetRendererFactory<?> assetRendererFactory =
				assetRendererFactories.get(i);

			if (!assetRendererFactory.isSearchable()) {
				continue;
			}

			assetTypes.add(assetRendererFactory.getClassName());
		}

		return ArrayUtil.toStringArray(assetTypes);
	}
	
	protected void addAssetEntriesFacet(SearchContext searchContext) {
		Facet assetEntriesFacet = new AssetEntriesFacet(searchContext);

		assetEntriesFacet.setStatic(true);

		searchContext.addFacet(assetEntriesFacet);
	}

	protected void addEnabledSearchFacets(long companyId, SearchContext searchContext) throws Exception {

		for (SearchFacet searchFacet : getEnabledSearchFacets()) {
			searchFacet.init(companyId, getSearchConfiguration(), searchContext);

			Facet facet = searchFacet.getFacet();

			if (facet == null) {
				continue;
			}

			searchContext.addFacet(facet);
		}
	}

	protected void addScopeFacet(SearchContext searchContext) {
		Facet scopeFacet = new ScopeFacet(searchContext);

		scopeFacet.setStatic(true);

		searchContext.addFacet(scopeFacet);
	}

	public List<SearchFacet> getEnabledSearchFacets() {

		List<SearchFacet> _enabledSearchFacets = ListUtil.filter(SearchFacetTracker.getSearchFacets(),
				new PredicateFilter<SearchFacet>() {

					@Override
					public boolean filter(SearchFacet searchFacet) {
						return isDisplayFacet(searchFacet.getClassName());
					}

				});

		return _enabledSearchFacets;
	}

	public String getSearchConfiguration() {

		String _searchConfiguration = _portletPreferences.getValue("searchConfiguration", StringPool.BLANK);

		return _searchConfiguration;
	}
	
	public boolean isDisplayFacet(String className) {
		return GetterUtil.getBoolean(
			_portletPreferences.getValue(className, null), true);
	}
	
	private PortletPreferences _portletPreferences;
	private int titleLength = 50;
}