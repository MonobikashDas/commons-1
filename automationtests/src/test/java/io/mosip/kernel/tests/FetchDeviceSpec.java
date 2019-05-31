
package io.mosip.kernel.tests;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.ITest;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;
import org.testng.internal.BaseTestMethod;
import org.testng.internal.TestResult;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Verify;

import io.mosip.kernel.service.ApplicationLibrary;
import io.mosip.kernel.util.CommonLibrary;
import io.mosip.kernel.util.KernelAuthentication;
import io.mosip.kernel.util.KernelDataBaseAccess;
import io.mosip.service.AssertKernel;
import io.mosip.service.BaseTestCase;
import io.mosip.util.TestCaseReader;
import io.restassured.response.Response;

/**
 * @author Ravi Kant
 *
 */
public class FetchDeviceSpec extends BaseTestCase implements ITest{
	
	
	FetchDeviceSpec() {
		super();
	}

	private static Logger logger = Logger.getLogger(FetchDeviceSpec.class);
	private final String jiraID = "MOS-8264";
	private final String moduleName = "kernel";
	private final String apiName = "FetchDeviceSpec";
	private final String requestJsonName = "FetchDeviceSpecRequest";
	private final String outputJsonName = "FetchDeviceSpecOutput";
	private final Map<String, String> props = new CommonLibrary().kernenReadProperty();
	private final String FetchDeviceSpec_lang_URI = props.get("FetchDeviceSpec_lang_URI").toString();
	private final String FetchDeviceSpec_id_lang_URI = props.get("FetchDeviceSpec_id_lang_URI").toString();

	protected String testCaseName = "";
	SoftAssert softAssert = new SoftAssert();
	boolean status = false;
	String finalStatus = "";
	public JSONArray arr = new JSONArray();
	Response response = null;
	JSONObject responseObject = null;
	private AssertKernel assertions = new AssertKernel();
	private ApplicationLibrary applicationLibrary = new ApplicationLibrary();
	KernelAuthentication auth=new KernelAuthentication();
	String cookie=null;

	/**
	 * method to set the test case name to the report
	 * 
	 * @param method
	 * @param testdata
	 * @param ctx
	 */
	@BeforeMethod(alwaysRun=true)
	public void getTestCaseName(Method method, Object[] testdata, ITestContext ctx) throws Exception {
		String object = (String) testdata[0];
		testCaseName = moduleName+"_"+apiName+"_"+object.toString();
		cookie = auth.getAuthForZonalApprover();
	}

	/**
	 * This data provider will return a test case name
	 * 
	 * @param context
	 * @return test case name as object
	 */
	@DataProvider(name = "fetchData")
	public Object[][] readData(ITestContext context)
			throws JsonParseException, JsonMappingException, IOException, ParseException {
			return TestCaseReader.readTestCases(moduleName + "/" + apiName, testLevel);
		}
	/**
	 * This fetch the value of the data provider and run for each test case
	 * 
	 * @param fileName
	 * @param object
	 * 
	 */
	@SuppressWarnings("unchecked")
	@Test(dataProvider = "fetchData", alwaysRun = true)
	public void fetchDeviceSpec(String testcaseName, JSONObject object)
			throws JsonParseException, JsonMappingException, IOException, ParseException {

		logger.info("Test Case Name:" + testcaseName);
		object.put("Test case Name", testcaseName);
		object.put("Jira ID", jiraID);

		String fieldNameArray[] = testcaseName.split("_");
		String fieldName = fieldNameArray[1];

		JSONObject requestJson = new TestCaseReader().readRequestJson(moduleName, apiName, requestJsonName);
		
		for (Object key : requestJson.keySet()) {
			if (fieldName.equals(key.toString()))
				object.put(key.toString(), "invalid");
			else
				object.put(key.toString(), "valid");
		}

		String configPath = "src/test/resources/" + moduleName + "/" + apiName
				+ "/" + testcaseName;
		
		File folder = new File(configPath);
		File[] listofFiles = folder.listFiles();
		JSONObject objectData = null;
		for (int k = 0; k < listofFiles.length; k++) {

			if (listofFiles[k].getName().toLowerCase().contains("request")) {
				objectData = (JSONObject) new JSONParser().parse(new FileReader(listofFiles[k].getPath()));
				logger.info("Json Request Is : " + objectData.toJSONString());

				if(objectData.containsKey("devicetypecode"))
					response = applicationLibrary.getRequestPathPara(FetchDeviceSpec_id_lang_URI, objectData,cookie);
					else
					response = applicationLibrary.getRequestPathPara(FetchDeviceSpec_lang_URI, objectData,cookie);

			} else if (listofFiles[k].getName().toLowerCase().contains("response")
					&& !testcaseName.toLowerCase().contains("smoke")) {
				responseObject = (JSONObject) new JSONParser().parse(new FileReader(listofFiles[k].getPath()));
				logger.info("Expected Response:" + responseObject.toJSONString());
			}
		}
		
		int statusCode = response.statusCode();
		logger.info("Status Code is : " + statusCode);
		//This method is for checking the authentication is pass or fail in rest services
		new CommonLibrary().responseAuthValidation(response);
		if (testcaseName.toLowerCase().contains("smoke")) {

			String queryPart = "select count(*) from master.device_spec";

			String query = queryPart;
			if (objectData != null) {
				if (objectData.containsKey("devicetypecode"))
					query = query + " where dtyp_code = '" + objectData.get("devicetypecode") + "' and lang_code = '"
							+ objectData.get("langcode") + "'";
				else
					query = queryPart + " where lang_code = '" + objectData.get("langcode") + "'";
			}
			long obtainedObjectsCount = new KernelDataBaseAccess().validateDBCount(query,"masterdata");

			// fetching json object from response
			JSONObject responseJson = (JSONObject) ((JSONObject) new JSONParser().parse(response.asString())).get("response");
			// fetching json array of objects from response
			JSONArray deviceSpecFromGet = (JSONArray) responseJson.get("devicespecifications");
			logger.info("===Dbcount===" + obtainedObjectsCount + "===Get-count===" + deviceSpecFromGet.size());
			
			// validating number of objects obtained form db and from get request
			if (deviceSpecFromGet.size() == obtainedObjectsCount) {

				// list to validate existance of attributes in response objects
				List<String> attributesToValidateExistance = new ArrayList();
				attributesToValidateExistance.add("id");
				attributesToValidateExistance.add("name");
				attributesToValidateExistance.add("brand");
				attributesToValidateExistance.add("model");
				attributesToValidateExistance.add("minDriverversion");
				attributesToValidateExistance.add("isActive");

				// key value of the attributes passed to fetch the data, should be same in all obtained objects
				HashMap<String, String> passedAttributesToFetch = new HashMap();
				if (objectData != null) {
					if (objectData.containsKey("devicetypecode")) {
						attributesToValidateExistance.add("deviceTypeCode");
						passedAttributesToFetch.put("deviceTypeCode", objectData.get("devicetypecode").toString());
						passedAttributesToFetch.put("langCode", objectData.get("langcode").toString());
					}
					passedAttributesToFetch.put("langCode", objectData.get("langcode").toString());
				}

				status = AssertKernel.validator(deviceSpecFromGet, attributesToValidateExistance, passedAttributesToFetch);
			} else
				status = false;

		}

		else {
			// add parameters to remove in response before comparison like time stamp
			ArrayList<String> listOfElementToRemove = new ArrayList<String>();
			listOfElementToRemove.add("responsetime");
			listOfElementToRemove.add("timestamp");
			status = assertions.assertKernel(response, responseObject, listOfElementToRemove);
		}

		if (status) {
			finalStatus = "Pass";
		} else {
			finalStatus = "Fail";
		}

		object.put("status", finalStatus);

		arr.add(object);
		boolean setFinalStatus = false;
		if (finalStatus.equals("Fail")) {
			setFinalStatus = false;
			logger.debug(response);
		} else if (finalStatus.equals("Pass"))
			setFinalStatus = true;
		Verify.verify(setFinalStatus);
		softAssert.assertAll();
	}

	@Override
	public String getTestName() {
		return this.testCaseName;
	}

	@AfterMethod(alwaysRun = true)
	public void setResultTestName(ITestResult result) {
		try {
			Field method = TestResult.class.getDeclaredField("m_method");
			method.setAccessible(true);
			method.set(result, result.getMethod().clone());
			BaseTestMethod baseTestMethod = (BaseTestMethod) result.getMethod();
			Field f = baseTestMethod.getClass().getSuperclass().getDeclaredField("m_methodName");
			f.setAccessible(true);
			f.set(baseTestMethod, testCaseName);
		} catch (Exception e) {
			Reporter.log("Exception : " + e.getMessage());
		}
	}

	/**
	 * this method write the output to corressponding json
	 */
	@AfterClass
	public void updateOutput() throws IOException {
		String configPath =  "src/test/resources/" + moduleName + "/" + apiName
				+ "/" + outputJsonName + ".json";
		try (FileWriter file = new FileWriter(configPath)) {
			file.write(arr.toString());
			logger.info("Successfully updated Results to " + outputJsonName + ".json file.......................!!");
		}
	}
}

