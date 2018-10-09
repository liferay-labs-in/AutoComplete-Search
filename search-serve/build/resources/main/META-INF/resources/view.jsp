<%@ include file="/init.jsp" %>

<style>

.search-container {
  padding: 25px 0;
}

.search-container .search-wrapper {
  position: relative;
}

.search-container .search-wrapper input[type="text"] {
  padding: 5px 10px;
  border: 2px solid;
}

.search-container .dropdown-menu {
  display: block;
}


</style>

<div class="search-container">
  	<div class="search-wrapper" id="resultsWrapper">
    	<input id="keywordsCustom" name="keywordsCustom" autocomplete="off" type="text" />
	</div>
</div>

<portlet:resourceURL id="/search/autocomplete" var="autoSearchURL" />

<aui:script>
jQuery('#keywordsCustom').on("keyup", function(){
	
	AUI().use('aui-io-request',function (A) {
	
		var keywordsPass=A.one("#keywordsCustom").get('value');
		keywordsPass = keywordsPass.trim();
		var myAjaxRequest=A.io.request('<%=autoSearchURL.toString()%>',{
			dataType: 'json',
			method:'POST',
			data:{
				<portlet:namespace />keywordsPass:keywordsPass,
			},
			autoLoad:false,
			sync:false,
			on: {
				
				success:function(){
				var data=this.get('responseData');
				console.log(data);
				var jsonData = data;
				
				var resultsWrapper = $('#resultsWrapper');
				var preTags ='<ul class="dropdown-menu">';
				var postTags ='</ul>';
				
				if(keywordsPass === ''){
					resultsWrapper.find('.dropdown-menu').remove();
				}
				else if(jsonData[0] === 'noresult'){
					resultsWrapper.find('.dropdown-menu').remove();
					resultsWrapper.append(preTags + "<li>No Results Found</li>" + postTags);
				}
				else{
					var jsonIndexData = '';	
					var customHtml = '';
					var keyName = '';
					var anchorNode = '';
					  	resultsWrapper.find('.dropdown-menu').remove();
					  for (var i = 0; i < jsonData.length; i++) {
					    jsonIndexData = jsonData[i];
					    for (var key in jsonIndexData) {
					      if (jsonIndexData.hasOwnProperty(key)) {
					        if (jsonIndexData[key].JSONArray.length) {
					          keyName = '<li class="dropdown-header">' + key + ':</li>';
					          anchorNode = '';
					          customHtml = customHtml + keyName;
					        }
					        for (var j = 0; j < jsonIndexData[key].JSONArray.length; j++) {
					          anchorNode = '<li><a href="' + jsonIndexData[key].JSONArray[j].ViewURL + '">' + jsonIndexData[key].JSONArray[j].Title + '</a></li>';
					          customHtml = customHtml + anchorNode;
					        }
					      }
					    }
					    resultsWrapper.append(preTags + customHtml + postTags);
					  }	
				}
								
			}}
		});
		myAjaxRequest.start();	
	});
});
</aui:script>
