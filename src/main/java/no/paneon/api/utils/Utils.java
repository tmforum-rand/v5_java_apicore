package no.paneon.api.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.HttpsURLConnection;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.awt.Dimension;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import no.paneon.api.graph.APIGraph;
import no.paneon.api.graph.APISubGraph;
import no.paneon.api.graph.Edge;
import no.paneon.api.graph.Property;
import no.paneon.api.model.APIModel;
import no.paneon.api.logging.AspectLogger;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Utils {
	
	static final Logger LOG = LogManager.getLogger(Utils.class);
	
	private Utils() {	
	}
	
    private static final Map<String, Level> levelmap = new HashMap<>();
	private static final Map<String,String> stereoTypeMapping = new HashMap<>();

    static {    	
 	    getLevelmap().put("verbose", AspectLogger.VERBOSE);
 	    getLevelmap().put("info", Level.INFO);
 		getLevelmap().put("error", Level.ERROR);
 		getLevelmap().put("debug", Level.DEBUG);
 		getLevelmap().put("trace", Level.TRACE);
 		getLevelmap().put("warn", Level.WARN);
 		getLevelmap().put("fatal", Level.FATAL);
 		getLevelmap().put("all", Level.ALL);
 		getLevelmap().put("off", Level.OFF);
  	
 		stereoTypeMapping.put("Ref", " <<Ref>>");
 		stereoTypeMapping.put("Relationship", " <<Ref>>");
 		
    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean isSimpleType(String type) {
		boolean res=false;
	    List<String> simpleTypes = Config.getSimpleTypes(); 
    
	    List<String> simpleEndings = Config.getSimpleEndings();       
	    List<String> nonSimpleEndings = Config.getNonSimpleEndings();
	    
        if(APIModel.isEnumType(type)) {
        	res=true;
        }
        else if(simpleTypes.contains(type)) {
	        res=true;
	    } else {
	        boolean nonSimple=nonSimpleEndings.stream().anyMatch(type::endsWith);
	        if(nonSimple) res=false;
	
	        res=simpleEndings.stream().anyMatch(type::endsWith);
	    }
        
        Out.debug("isSimpleType: type={} res={}", type, res);
        
        return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getStereoType(APIGraph aPIGraph, String node, String pivot, List<String> subGraphs) {
		List<String> allResources = APIModel.getResources();
		
		if(allResources.contains(node) && !node.contentEquals(pivot)) {
			return " <<DiscriminatorNode>>";
		} else if(node.contains("_discriminator")) {
			return " <<DiscriminatorNode>>";
		} else if(node.equals(pivot) ) {
	        return (aPIGraph instanceof APISubGraph) ? " <<SubResource>>" : " <<Pivot>>";
	    } else if( subGraphs.contains(node)) {
	        return " <<SubResourceReference>>" ;
	    } else {
	        String res = "<<Resource>>";
	        List<Property> props = aPIGraph.getNode(node).getProperties();
	        boolean hasRef = props.stream().map(Property::getName).anyMatch(s -> s.equals("href"));
	        if(hasRef) {
	        	Optional<String> key = stereoTypeMapping.keySet().stream().filter(node::endsWith).findFirst();
	        	if(key.isPresent()) {
	        		res=stereoTypeMapping.get(key.get());
	        	} else {
	        		Map<String,String> specialStereoType = Config.getMap("specialStereoType");
	        		if(specialStereoType.containsKey(node)) {
	        			res=specialStereoType.get(node);
	        		}
	        	}
	        }
	        return res;
	    }
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean isEnumType(JSONObject swagger, String type) {
	    boolean res=false;
	    JSONObject def = getDefinition(swagger,type);
	    if(def!=null)
	        res = def.has("enum");
        LOG.trace("isEnumType: checking type={} res={}", type, res);
	    return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> JSONArrayToList(JSONArray array) {
		List<String> res = new ArrayList<>();
		Iterator<Object> it = array.iterator();
	    while (it.hasNext()) {
	    	res.add((String)it.next());
	    }
	    return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSONOrYaml(String source) {
		JSONObject res = null;
		try {
			if(source.endsWith(".yaml") || source.endsWith(".yml")) {
				res = readYamlAsJSON(source,false);
			} else {
				res = readJSON(source,false);
			}
		} catch(Exception e) {
			Out.println("... unable to read source " + getBaseFileName(source) + " (error: " + e.getLocalizedMessage() + ")");
			// e.printStackTrace();
			System.exit(0);
		}
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSONOrYaml(InputStream file) {
		JSONObject res = null;
		try {
	        String content = IOUtils.toString(file, StandardCharsets.UTF_8); 
	        
	        if(!content.isEmpty()) {
	        	LOG.debug("readJSONOrYaml: content={}", content.subSequence(0, Math.min(30,  content.length()-1)));
	        }
	        
	        if(isYaml(content)) {
	        	res = convertYamlAsJSON(content,false);
	        } else {
				res = convertJSON(content,false);
	        }
	        
		} catch(Exception e) {
			Out.println("... unable to read source: : error: " + e.getLocalizedMessage() );
			// e.printStackTrace();
			System.exit(0);
		}
		return res;
	}
	
	private static boolean isYaml(String content) {
		String prefix = content.substring(0, Math.min(300, content.length())).replaceAll(" ", "");
		return !prefix.startsWith("{") && !content.startsWith("[");
	} 

	@LogMethod(level=LogLevel.TRACE)
	public static String getBaseFileName(String file) {
		File f = new File(file);
		return f.getName();
	}

//	@LogMethod(level=LogLevel.TRACE)
//	public static JSONObject readYamlAsJSON(String fileName, boolean errorOK) throws Exception {
//		try {
//			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
//	        File file = new File(path);
//	        String yaml = FileUtils.readFileToString(file, "utf-8");
//	        String json = convertYamlToJson(yaml);
//	        return new JSONObject(json); 
//		} catch(Exception ex) {
//			if(!errorOK) throw(ex);
//			return new JSONObject();
//		}
//    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject convertYamlAsJSON(String yaml, boolean errorOK) throws Exception {
		try {
	        String json = convertYamlToJson(yaml);
	        return new JSONObject(json); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }

//	@LogMethod(level=LogLevel.TRACE)
//	public static String readFile(String fileName) {
//		try {
//			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
//	        File file = new File(path);
//	        String res = FileUtils.readFileToString(file, "utf-8");
//	        return res;
//		} catch(Exception ex) {
//			Out.printAlways("... unable to read from file: " + fileName);
//			return "";
//		}
//    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static String readFile(String fileName) throws Exception {
		String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
        File file = new File(path);
        return FileUtils.readFileToString(file, "utf-8");
    }
	
//	@LogMethod(level=LogLevel.TRACE)
//	public static String convertJsonToYaml(JSONObject json) throws Exception {
//		YAMLFactory yamlFactory = new YAMLFactory()	
//			 .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES) 
//	         .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
//	         // .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
//	         ;
//		
//		YAMLMapper mapper = new YAMLMapper(yamlFactory);
//	    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
//
//		
//	    ObjectMapper jsonMapper = new ObjectMapper();
//	    jsonMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
//	    
//	    jsonMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
//
//	    JsonNode json2 = mapper.readTree(json.toString());
//	    
//	    final Object obj = jsonMapper.treeToValue(json2, Object.class);
//	    final String jsonString = jsonMapper.writeValueAsString(obj);
//
//	    LOG.debug("convertJsonToYaml: json={}", jsonString);
//	    
//	    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
//        String jsonAsYaml = mapper.writeValueAsString(jsonNodeTree);
//        return jsonAsYaml;
//        
//	}
//	
//	@LogMethod(level=LogLevel.TRACE)
//    static String convertYamlToJson(String yaml) throws Exception {
//	    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
//	    Object obj = yamlReader.readValue(yaml, Object.class);
//
//	    ObjectMapper jsonWriter = new ObjectMapper();
//	    return jsonWriter.writeValueAsString(obj);
//	}
//	
//	@LogMethod(level=LogLevel.TRACE)
//	public static JSONObject readJSON(String fileName, boolean errorOK) throws Exception {
//		try {
//			String path = fileName.replaceFirst("^~", System.getProperty("user.home"));
//	        File file = new File(path);
//	        String content = FileUtils.readFileToString(file, "utf-8");
//	        return new JSONObject(content); 
//		} catch(Exception ex) {
//			if(!errorOK) throw(ex);
//			return new JSONObject();
//		}
//    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject convertJSON(String content, boolean errorOK) throws Exception {
		try {
	        return new JSONObject(content); 
		} catch(Exception ex) {
			if(!errorOK) throw(ex);
			return new JSONObject();
		}
    }

	@LogMethod(level=LogLevel.TRACE)
	public static void saveJSON(JSONObject json, String destination) {
		
		try {
			if(destination.endsWith(".yaml") || destination.endsWith(".yml")) {
				String text = convertJsonToYaml(json);
				save(text, destination);
			} else {
				String text = json.toString(2);
				save(text, destination);
			}
		} catch(Exception ex) {
			Out.println("unable to write to file: ", destination);
			System.exit(1);
		}
		
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static void save(String text, String destination) {
				
		LOG.debug("save:: destination={}",  destination);
		
		if(destination==null) {
			Out.println(text);
		} else {

			createDirectory(destination);

			try(FileWriter out = new FileWriter(destination)) {
				out.write(text);
				out.close();
				
			} catch(Exception e) {
				Out.println("unable to write to file: ", destination);
				System.exit(1);
			}
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static void createDirectory(String destination) {
		File file = new File(destination);

		String parent = file.getParent();
		
		if(parent!=null) {
			File dir = new File(parent);
			if(!dir.exists()) {
				dir.mkdirs();
			}
		}
	
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean copyFile(String sourceFile, String destinationFile) {
		boolean NOT_OVERWRITE=false;
		return copyFile(sourceFile,destinationFile,NOT_OVERWRITE);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean copyFile(String sourceFile, String destinationFile, boolean overwrite) {

		File source = new File(sourceFile);
	    File dest = new File(destinationFile);
	
	    if(!source.exists()) {
	    	Out.println("... source file (" + sourceFile + ") does not exists - copy not performed" );
	    	return true;
	    }

	    if(dest.exists() && !overwrite) {
	    	Out.println("... file '" + destinationFile + "' exists - not overwritten" );
	    	return true;
	    }
	    
		try {
			createDirectory(destinationFile);

			CopyOption options = StandardCopyOption.REPLACE_EXISTING;
			
		    Files.copy(source.toPath(), dest.toPath(), options );	
		    
		    return true;
		
		} catch(Exception e) {
			Out.println("unable to copy file: source=" + sourceFile + " destination=" + destinationFile);
			
			return false;
			
		}
	    
	}

	public enum CopyStyle {
		OVERWRITE,
		KEEP_ORIGINAL
	}
	
	public static final CopyStyle OVERWRITE = CopyStyle.OVERWRITE;
	public static final CopyStyle KEEP_ORIGINAL = CopyStyle.KEEP_ORIGINAL;

	@LogMethod(level=LogLevel.TRACE)
	public static boolean copyFile(String sourceFile, String destinationFile, String destinationDirectory, String sourceDirectory) {
		return copyFile(sourceFile, destinationFile, destinationDirectory, sourceDirectory, CopyStyle.KEEP_ORIGINAL);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static boolean copyFile(String sourceFile, String destinationFile, String destinationDirectory, String sourceDirectory, CopyStyle copyStyle) {

		try {
		
			LOG.debug("copyFile: sourceFile={}, sourceDirectory={} destinationFile={}", sourceFile, sourceDirectory, destinationFile);
			
			InputStream is = Utils.getFileInputStream(sourceFile, sourceFile, sourceDirectory, "./" );
			
			File dest = Utils.getFile(destinationFile, destinationFile, destinationDirectory);
			
			createDirectory(dest.getAbsolutePath());
			
			if(copyStyle==KEEP_ORIGINAL && dest.exists()) {
		    	Out.println("... file '" + destinationFile + "' exists - not overwritten" );
		    	return true;
			}
			
			return copy(is, new FileOutputStream(dest));
						
			
		} catch(Exception ex) {			
			LOG.debug("unable to copy file: source={} destination={}", sourceFile, destinationFile);
			LOG.debug("exception: {}", ex.getLocalizedMessage());
			
			return false;
			
		}
	    
	}

	@LogMethod(level=LogLevel.TRACE)
	private static boolean copy(InputStream source, OutputStream target) throws IOException {
	    byte[] buf = new byte[8192];
	    int length;
	    while ((length = source.read(buf)) > 0) {
	        target.write(buf, 0, length);
	    }
	    return true;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<String> extractResources(JSONObject swagger) {
		List<String> res = new ArrayList<>();
		
		if(isOpenAPIv2(swagger)) {
			swagger.getJSONObject("paths").keySet().forEach( path ->  {
	
				JSONObject pathObj = swagger.getJSONObject("paths").getJSONObject(path);
				pathObj.keySet().forEach( op -> {
					JSONObject opObj = pathObj.getJSONObject(op);
					opObj.getJSONObject("responses").keySet().forEach( resp -> {
						if(!"default".equals(resp) && Integer.parseInt(resp)<300) {
							JSONObject respObj = opObj.getJSONObject("responses").getJSONObject(resp);
							if(respObj.has("schema")) {
								JSONObject schema = respObj.getJSONObject("schema");
								if(schema.has("$ref")) {
									res.add(schema.getString("$ref"));
								} else if(schema.has("items") && schema.getJSONObject("items").has("$ref")) {
									res.add(schema.getJSONObject("items").getString("$ref"));
								}
							}
						}
					});
				});
			});
		} else {
			
			swagger.getJSONObject("paths").keySet().forEach( path ->  {	
				JSONObject pathObj = swagger.getJSONObject("paths").getJSONObject(path);
				pathObj.keySet().forEach( op -> {
					JSONObject opObj = pathObj.getJSONObject(op);
					opObj.getJSONObject("responses").keySet().forEach( resp -> {
						if(!"default".equals(resp) && Integer.parseInt(resp)<300) {
							JSONObject respObj = opObj.getJSONObject("responses").getJSONObject(resp);
							if(respObj.has("content")) {
								JSONObject content = respObj.getJSONObject("content");
								Optional<String> key = content.keySet().stream().filter(k -> k.startsWith("application/json")).findFirst();
								if(key.isPresent()) content=content.optJSONObject(key.get());
								if(content.has("schema")) {
									JSONObject schema = content.getJSONObject("schema");
									if(schema.has("$ref")) {
										res.add(schema.getString("$ref"));
									} else if(schema.has("items") && schema.getJSONObject("items").has("$ref")) {
										res.add(schema.getJSONObject("items").getString("$ref"));
									}
								}
							}
						}
					});
				});
			});
		}
			
		return res;
		
	}
	
	@LogMethod(level=LogLevel.TRACE)
	private static boolean isOpenAPIv2(JSONObject swagger) {
		return !swagger.has("openapi");
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Collection<String> getAllDefinitions(JSONObject swagger) {
		Set<String> res = new HashSet<>();
		JSONObject definitions = getDefinitions(swagger);
		if(definitions!=null) res = definitions.keySet();
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> String dump(Collection<T> collection) {
		return collection.stream().map(Object::toString).collect(Collectors.joining(", "));
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> SortedSet<T> intersection(List<T> al, List<T> bl) {
		Set<T> a = new TreeSet<>(al);
		Set<T> b = new TreeSet<>(bl);
		return intersection(a,b);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static <T> SortedSet<T> intersection(List<T> al, Set<T> b) {
		Set<T> a = new TreeSet<>(al);
		return intersection(a,b);
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> SortedSet<T> intersection(Set<T> a, Set<T> b) {
	    if (a.size() > b.size()) {
	        return intersection(b, a);
	    }

	    SortedSet<T> results = new TreeSet<>();

	    for (T element : a) {
	        if (b.contains(element)) {
	            results.add(element);
	        }
	    }

	    return results;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> difference(List<T> al, List<T> bl) {		
		Set<T> a = new TreeSet<>(al);
		Set<T> b = new TreeSet<>(bl);
		
		a.removeAll(b);

	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> difference(List<T> al, Set<T> b) {
		Set<T> a = new TreeSet<>(al);
		a.removeAll(b);
	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> difference(Set<T> al, Set<T> b) {
		Set<T> a = new TreeSet<>(al);
		a.removeAll(b);
	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> union(List<T> al, List<T> bl) {		
		Set<T> a = new TreeSet<>(al);
		Set<T> b = new TreeSet<>(bl);
		
		a.addAll(b);

	    return a;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> union(Set<T> a, Set<T> b) {	
		Set<T> res = new TreeSet<>(a);
		
		res.addAll(b);

	    return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getEnumValues(JSONObject swagger, String node) {
		List<String> res = new LinkedList<>();
		JSONObject def = Utils.getDefinition(swagger,node);
		if(def!=null && def.has("enum")) {
			JSONArray values = def.optJSONArray("enum");
			if(values!=null) res.addAll(values.toList().stream().map(Object::toString).collect(toList()));
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getDefinition(JSONObject swagger, String node) {
		JSONObject res=null;
		JSONObject definitions = Utils.getDefinitions(swagger);
		if(definitions!=null) {
			res = definitions.optJSONObject(node);
		}
		return res;	
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Collection<String> getAllReferenced(JSONObject swagger) {
		List<String> res = new LinkedList<>();
		
		getAllDefinitions(swagger).forEach(resource -> {
			res.addAll(getAllReferenced(swagger,resource));
		});
		
		return res.stream().distinct().collect(toList());
	}

	@LogMethod(level=LogLevel.TRACE)
	private static Collection<? extends String> getAllReferenced(JSONObject swagger, String resource) {
		List<String> res = new LinkedList<>();
		
		JSONObject def = getDefinition(swagger, resource);
		if(def!=null && def.has("properties") && def.optJSONObject("properties")!=null) {
			final JSONObject property = def.optJSONObject("properties");
			property.keySet().forEach(prop -> {
				JSONObject o = property.optJSONObject(prop);
				if(o.has("$ref")) {
					String ref = o.optString("$ref");
					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
				} else if (o.has("items")) {
					String ref = o.optJSONObject("items").optString("$ref");
					if(!ref.isEmpty()) res.add( lastElement(ref,"/") );
				}
			});
		}
		
		return res;		
	}


	@LogMethod(level=LogLevel.TRACE)
	private static String lastElement(String ref, String delim) {
		String[] s = ref.split(delim);
		return s[s.length-1];
	}

	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject getDefinitions(JSONObject swagger) {
		JSONObject res=null;
		if(Utils.isOpenAPIv2(swagger))
			res=swagger.getJSONObject("definitions");
		else {
			JSONObject components = swagger.optJSONObject("components");
			if(components!=null) res = components.optJSONObject("schemas");
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> extractResourcesFromRules(String rulesFile) {
		List<String> res = new LinkedList<>();
		
		if(rulesFile==null) return res;
		
		try {
			JSONObject rules = readYamlAsJSON(rulesFile,true);
			Iterator<String> iter = rules.keySet().iterator();
			if(iter.hasNext()) {
				String apiKey = iter.next();
				if(rules.has(apiKey)) rules = rules.optJSONObject(apiKey);
				if(rules!=null && rules.has("resources")) {
					JSONArray resources = rules.optJSONArray("resources");
					res = resources.toList().stream().map(Object::toString).collect(toList());
				}
			}
		} catch(Exception e) {
			LOG.error("unable to read API rules from " + rulesFile);
		}
		return res;

	}

	@LogMethod(level=LogLevel.TRACE)
	public static boolean isBaseType(String pivot, String resource) {
		boolean res=false;
		JSONObject config = Config.getBaseTypes();
		if(config.has(pivot)) {
			JSONArray baseTypes = config.optJSONArray(pivot);
			res = baseTypes.toList().stream().map(Object::toString).anyMatch(s -> s.contentEquals(resource));
		} else if(config.has("common")) {
			JSONArray baseTypes = config.optJSONArray("common");
			res = baseTypes.toList().stream().map(Object::toString).anyMatch(s -> s.contentEquals(resource));			
		}
 		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static <T> List<T> copyList(List<T> list) {
		return list.stream().collect(toList());  
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static <T> Set<T> copySet(Set<T> set) {
		return set.stream().collect(toSet());  
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getFileName(String workingDirectory, JSONObject config, String property) {
		return workingDirectory + File.separator + config.optString(property);				
	}

	// get InputStream from either the fileName or embedded through the property
	@LogMethod(level=LogLevel.TRACE)
	public static InputStream openFileStream(String workingDirectory, String fileName, String property) throws IOException {
		
		if(fileName!=null) {
			return openFileStream(workingDirectory, fileName);
		} else {
			fileName = Config.getString(property); 
			return new ClassPathResource("/" + fileName).getInputStream();
		}
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static File getFile(String fileName, String property, String directory) throws IOException {

		String candidate = Config.getString(property); 
		
		if(fileName !=null && ".".contentEquals(fileName)) {
			fileName="";
		}
				
		if(fileName==null || fileName.isEmpty()) {
			fileName=candidate;
		}
		
		if(directory!=null && !directory.isEmpty())	{
			fileName = directory + File.separator + fileName;
		}
		
		return new File(fileName);	

	}


	@LogMethod(level=LogLevel.TRACE)
	public static InputStream getSource(String source, List<String> directories) throws Exception {
		InputStream res=null;
					
		try {
			URI uri = new URI(source);
			boolean isWeb = uri.getScheme()!=null && Arrays.asList("HTTP", "HTTPS").contains(uri.getScheme().toUpperCase());
			if(isWeb) {
				URL url = uri.toURL(); 
				URLConnection con = url.openConnection();   

				LOG.debug("getSource::isWeb={} url={}",  isWeb, source);

				res = con.getInputStream();		   
			} else {
				res = new FileInputStream(source);
			}
		} catch(Exception ex) {
			Out.debug("ERROR: {}",  ex.getLocalizedMessage());
			try {
				res = new FileInputStream(source);
			} catch(Exception ey) {
				Out.printOnce("... unable to read source {}", source);
			}
		}
		
		LOG.debug("getSource::HostnameVerifier={}",  HttpsURLConnection.getDefaultHostnameVerifier());
		LOG.debug("getSource::SSLSocketFactory={}",  HttpsURLConnection.getDefaultSSLSocketFactory());
		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static File getFile(String fileName, List<String> directories) throws Exception {
		File res=null;
						
		directories.add("");
		
		LOG.debug("directories={}",  directories);

		boolean found=false;
		Iterator<String> iter = directories.iterator();
		while(!found && iter.hasNext()) {
			
			String candFileName=iter.next();
			
			if(!candFileName.isEmpty()) {
				candFileName = candFileName + File.separator;
			}
			
			candFileName = candFileName + fileName;
						
			LOG.debug("candFileName={}",  candFileName);

			File file = new File(candFileName);	
			if(file.exists()) {
				res = file;
				found=true;
				
				LOG.debug("exists res={}", res);

			} 		
		}
			
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static InputStream getFileInputStream(String fileName, String property, String ... directories) throws IOException {
		InputStream res=null;

		String candidate = Config.getString(property); 

		List<String> sourceDirs = new LinkedList<>(Arrays.asList(directories));
		
		if(fileName !=null && ".".contentEquals(fileName)) {
			sourceDirs.add(".");
			sourceDirs.add("");
			fileName="";
		}
		sourceDirs.add("");
		sourceDirs.add(".");
		sourceDirs.add( System.getProperty("user.dir") );
		
		LOG.debug("dirs={}",  sourceDirs);
		
		if(fileName==null || fileName.isEmpty()) {
			fileName=candidate;
		}
		
		boolean found=false;
		Iterator<String> iter = sourceDirs.iterator();
		while(!found && iter.hasNext()) {
			
			String candFileName=iter.next();
			
			if(!candFileName.isEmpty()) {
				candFileName = candFileName + File.separator;
			}
			
			candFileName = candFileName + fileName;
						
			LOG.debug("candFileName={}",  candFileName);

			File file = new File(candFileName);	
			if(file.exists()) {
				res = new FileInputStream(file);
				found=true;
				
				LOG.debug("exists res={}", res);

			} 		
		}
			
		if(!found) {
			LOG.debug("not found - trying with ClassPathResource");

			String source = candidate.isEmpty() ? fileName : candidate;
			res = new ClassPathResource("/" + source).getInputStream();
			LOG.debug("source={}", source);
			LOG.debug("path res={}", res);
		}
			
		LOG.debug("res={}", res);

		return res;
	}


	@LogMethod(level=LogLevel.TRACE)
	public static InputStream openFileStream(String workingDirectory, String fileName) throws IOException {
		fileName = Utils.getFilenameWithDirectory(workingDirectory, fileName);	
		return openFileStream(fileName);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static InputStream openFileStream(String fileName) throws IOException {
		return new FileInputStream(new File(fileName));
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String getFilenameWithDirectory(String directory, String fileName) {
		try {
			if(!fileName.isEmpty()) {	
				File file = new File(fileName);
				if (!file.isAbsolute() && !directory.equals(".")) {
					fileName = directory + File.separator + fileName;
				}
			}
		} catch(Exception e) {		
			
		}
		return fileName;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static String lowerCaseFirst(String str) {
		if(str.isEmpty())
			return str;
		else
			return str.substring(0, 1).toLowerCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String upperCaseFirst(String str) {
		if(str.isEmpty())
			return str;
		else
			return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String pascalCase(String str) {
		str = str.toLowerCase();
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String, Level> getLevelmap() {
		return levelmap;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String getOptString(JSONObject conf, String property) {
		String res="";
		if(conf!=null && conf.has(property)) res = conf.get(property).toString();
		if("null".equals(res)) res = "";
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static Map<String,JSONObject> getJSONObjectMap(JSONObject obj) {
		Map<String,JSONObject> res = new HashMap<>();
		
		if(obj!=null) {
			for(String key : obj.keySet()) {
				JSONObject value = obj.optJSONObject(key);
				if(value!=null) {
					res.put(key, value);
				}
			}
		}		
		return res;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static Dimension getImageDim(final String path, File file) {
	    Dimension result = null;
	    String suffix = getFileSuffix(path);
	    Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);
	    	    
	    if (iter.hasNext()) {
	        ImageReader reader = iter.next();
	        try {
	            ImageInputStream stream = new FileImageInputStream(file);
	            reader.setInput(stream);
	            int width = reader.getWidth(reader.getMinIndex());
	            int height = reader.getHeight(reader.getMinIndex());
	            result = new Dimension(width, height);
	        } catch (IOException e) {
	            LOG.debug(e.getMessage());
	        } finally {
	            reader.dispose();
	        }
	    } else {
	        LOG.debug("No reader found for given format: {}", suffix);
	    }
	    return result;
	}

	@LogMethod(level=LogLevel.TRACE)
	private static String getFileSuffix(final String path) {
	    String result = null;
	    if (path != null) {
	        result = "";
	        if (path.lastIndexOf('.') != -1) {
	            result = path.substring(path.lastIndexOf('.'));
	            if (result.startsWith(".")) {
	                result = result.substring(1);
	            }
	        }
	    }
	    return result;
	}
	
	@LogMethod(level=LogLevel.TRACE)
	public static List<Object> merge(List<Object> listA, List<Object> listB) {
		List<Object> res = new LinkedList<>(listA);
		List<Object> tmp = new LinkedList<>(listB);
		
		tmp.removeAll(listA);
		
		res.addAll(tmp);
		
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> filterList(List<String> all, Collection<String> retain) {	
		List<String> res = new LinkedList<>(all);
		
		res.retainAll(retain);

	    return res;
	}
		

	@LogMethod(level=LogLevel.TRACE)
	public static void saveAsJson(JSONObject obj, String destination) {
		String text = obj.toString(2);
		save(text,destination);
	}

	@LogMethod(level=LogLevel.TRACE)
	public static void saveAsYaml(JSONObject obj, String destination) {
		try {
			String text = Utils.convertJsonToYaml(obj);	
						
			// add more space between each first level element 
			// and in front of every second level (assuming indented by two spaces)
			text = text.replaceAll("\n([A-Za-z][^:]*:)", "\n\n$1");
			text = text.replaceAll("\n(  [A-Za-z][^:]*:)", "\n\n$1");

			save(text,destination);
			
		} catch(Exception e) {
			Out.println("error converting to yaml file: ", destination);
			Out.println("error: ", e.getLocalizedMessage());
			System.exit(1);
		}
	}

	public static String replaceVariables(String paragraph, Map<String, String> variables) {		
		String res = paragraph;		
		for (Entry<String,String> variable : variables.entrySet()) {
			String field = "${" + variable.getKey() + "}";			
			if (res.contains(field)) {				
				res = res.replace(field, variable.getValue());	
			}
		}
		return res;
	}

	public static <T> Optional<T> getFirstElement(Collection<T> collection) {
		Optional<T> res = Optional.empty();
		Iterator<T> iter = collection.iterator();
		if(iter.hasNext()) {
			res = Optional.of(iter.next());
		}
		return res;
	}

	public static List<JSONObject> convertJSONArrayToJSONObjectList(JSONArray args) {
		List<JSONObject> res = new LinkedList<>();
		
		for(int i=0; i<args.length(); i++) {
			if(args.optJSONObject(i)!=null) {
				res.add( args.optJSONObject(i));
			}
		}
		
		return res;
		
	}	
	
	private static final String EXCEPTION_MESSAGE  = "exeption: {}";
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readJSON(String source, boolean errorOK) throws AppException {
		
		String content;

		try {
						
			if(isWebSource(source)) {
				LOG.debug("isWebSource readJSON::source={}", source );

				source = source.replace("\\", "/");
	
				URI uri = new URI(source);
				URL url = uri.toURL(); 
				URLConnection conn = url.openConnection();
				InputStream is = conn.getInputStream();
			    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		        		        
			} else {
				LOG.debug("fileSource readJSON::source={}", source );

				String path = source.replaceFirst("^~", System.getProperty("user.home"));
		        File file = new File(path);
		        content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

			}
	        
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			LOG.debug("... ERROR readJson exception={}", ex.getLocalizedMessage() );
			if(!errorOK) throw(new AppException(ex.getLocalizedMessage()));
			return new JSONObject();
		}
		
		try {
			return new JSONObject(content); 
			
		} catch(Exception ex) {
			Out.printAlways("... error when reading JSON: {}", ex.getLocalizedMessage() );
			if(!errorOK) throw(new AppException(ex.getLocalizedMessage()));
			return new JSONObject();
		}
    }
	
	@LogMethod(level=LogLevel.TRACE)
	public static JSONObject readYamlAsJSON(String source, boolean errorOK) throws AppException {
		try {
			String yaml;
			
			if(isWebSource(source)) {
				LOG.debug("readYamlAsJSON::source={}", source );

				URI uri = new URI(source);
				URL url = uri.toURL(); 
				URLConnection conn = url.openConnection();
				InputStream is = conn.getInputStream();
			    yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
		        		        
			} else {
				String path = source.replaceFirst("^~", System.getProperty("user.home"));
		        File file = new File(path);
		        yaml = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

			}
			
	        String json = convertYamlToJson(yaml);
	        return new JSONObject(json); 

		} catch(Exception ex) {
			Out.debug("readYamlAsJSON::source={} exception={}", source, ex.getLocalizedMessage() );
			// ex.printStackTrace();
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			if(!errorOK) throw(new AppException(ex.getLocalizedMessage()));
			return new JSONObject();
		}
    }
	
	private static boolean isWebSource(String source) {
		boolean res=false;
		try {
			String src=source.toUpperCase();
			res=src.startsWith("HTTP");
			
//			URI uri = new URI(source);
//			res = Arrays.asList("HTTP", "HTTPS").contains(uri.getScheme().toUpperCase());
		} catch(Exception ex) {
			Out.debug("isWebSource:: execption={}",  ex.getLocalizedMessage());
			// and ignore
		}
		return res;
	}

	@LogMethod(level=LogLevel.TRACE)
	public static String convertJsonToYaml(JSONObject json) throws AppException {
		try {
			YAMLFactory yamlFactory = new YAMLFactory()	
				 .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES) 
		         .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
		         .enable(YAMLGenerator.Feature.INDENT_ARRAYS)
		         ;
			
			ObjectMapper mapper = new ObjectMapper(yamlFactory);		 
		    SimpleModule module = new SimpleModule();
		    
		    module.setSerializerModifier(new YamlBeanSerializerModifier());
		    mapper.registerModule(module);
		    
		    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
	
		    return mapper.writeValueAsString(json);
		    
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			throw(new AppException(ex.getLocalizedMessage()));
		}
		        
	}
	
	@LogMethod(level=LogLevel.TRACE)
    public static String convertYamlToJson(String yaml) throws AppException {
				
		try {
		    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
		    Object obj = yamlReader.readValue(yaml, Object.class);
			    
		    ObjectMapper jsonWriter = new ObjectMapper();
	
		    return jsonWriter.writeValueAsString(obj);		    
		    			
		} catch(Exception ex) {
			if(LOG.isDebugEnabled()) LOG.log(Level.DEBUG, EXCEPTION_MESSAGE, ex.getLocalizedMessage() );
			throw(new AppException(ex.getLocalizedMessage()));
		}
	}

	@LogMethod(level=LogLevel.TRACE)
	public static List<String> getFiles(String fileType, String ... dirArgs) {
		
		String dir = Arrays.asList(dirArgs).stream().collect(Collectors.joining("/"));
		
    	List<String> res = Stream.of(new File(dir).listFiles())
					        .filter(file -> !file.isDirectory())
					        .map(File::getName)
					        .filter(f -> f.endsWith(fileType))
					        .collect(Collectors.toList());
    	
    	return res;
    	
	}

	public static String getLastPart(String str, String separator) {
		if(separator.contentEquals(".")) separator = "\\.";
		String parts[] = str.split(separator);
		
		return parts.length>0 ? parts[parts.length-1] : str;
	}

	public static String getRelativeFile(String baseFilename, String relativeFilename) {
		
		LOG.debug("getRelativeFile: baseFilename={} relativeFilename={}", baseFilename, relativeFilename);

		String res = "";
		if(isWebSource(baseFilename)) {
			try {
				res = baseFilename;
				
				URI uri = new URI(baseFilename);
				String path = uri.getPath();
				
				String newPath = getRelativeFile(path, relativeFilename);
				res = res.replace(path, newPath);
				
				LOG.debug("getRelativeFile: WEB path={} newPath={} res={}", path, newPath, res);

			} catch(Exception e) {
				// ignore
			}
			
			
		} else {
			String baseDirectory=new File(baseFilename).getParent();
			if(!baseDirectory.endsWith("/")) {
				res=baseDirectory + "/" + relativeFilename;
			} else {
				res=baseDirectory + relativeFilename;
			}
				
			Path path = Path.of(res);
			boolean exists = Files.exists(path);
					
			LOG.debug("getRelativeFile: NOT web baseDirectory={} path={} res={}", baseDirectory, path, res);

			if(!exists) {
				LOG.debug("getRelativeFile: relativeFilename={} candidate={} exists={}", relativeFilename, res, exists);
		
			}
				
		}

		LOG.debug("getRelativeFile: relativeFilename={} candidate={} exists={}", relativeFilename, res);

		return res;

	}

	
	
}
