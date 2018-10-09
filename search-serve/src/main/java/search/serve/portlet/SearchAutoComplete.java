package search.serve.portlet;

import com.liferay.portal.kernel.portlet.bridges.mvc.MVCResourceCommand;
import com.liferay.portal.kernel.util.ParamUtil;

import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import org.osgi.service.component.annotations.Component;

@Component(immediate = true, property = { "javax.portlet.name=com_liferay_portal_search_web_portlet_SearchPortlet",
		"mvc.command.name=/search/autocomplete" }, service = MVCResourceCommand.class)
public class SearchAutoComplete implements MVCResourceCommand {

	PortletPreferences portletPreferences = null;
	
	@Override
	public boolean serveResource(ResourceRequest resourceRequest, ResourceResponse resourceResponse)
			throws PortletException {

		System.out.println(":::Calling serve:::" + ParamUtil.getString(resourceRequest, "keywordsPass"));
		return false;
	}
	
}