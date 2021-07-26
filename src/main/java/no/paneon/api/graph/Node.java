package no.paneon.api.graph;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toList;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jgrapht.Graph;
import org.json.JSONArray;
import org.json.JSONObject;

import no.paneon.api.model.APIModel;
import no.paneon.api.utils.Config;
import no.paneon.api.utils.Out;
import no.paneon.api.logging.LogMethod;
import no.paneon.api.logging.AspectLogger.LogLevel;

public class Node implements Comparable<Object>  {

    static final Logger LOG = LogManager.getLogger(Node.class);

	List<Property> properties;
		
	Map<Place,List<Node>> placements;
	
	String description = "";

	List<OtherProperty> otherProperties;

	String resource = "ANON";
	
	List<String> enums; 
	
	Set<String> inheritance;
	
	Set<String> discriminatorMapping;
	Set<String> externalDiscriminatorMapping;
	Set<String> inheritedDiscriminatorMapping;

	String inline = "";
	
	static final String ALLOF = "allOf";
	static final String PROPERTIES = "properties";
	static final String TYPE = "type";
	static final String ARRAY = "array";
	static final String ITEMS = "items";
	static final String ENUM = "enum";
	static final String NULLABLE = "nullable";
	static final String REQUIRED = "required";

	static final String DISCRIMINATOR = "discriminator";
	
	static final String DESCRIPTION = "description";
	static final String REF = CoreAPIGraph.REF;
	static final String INCLUDE_INHERITED = CoreAPIGraph.INCLUDE_INHERITED;

	static final String EXPAND_INHERITED = "expandInherited";
	static final String EXPAND_ALL_PROPERTIES_FROM_ALLOFS = "expandPropertiesFromAllOfs";
	
	private Node() {
		properties = new LinkedList<>();
		placements = new EnumMap<>(Place.class);
		
		otherProperties = new LinkedList<>();
		
		enums = new LinkedList<>();
		
		inheritance = new HashSet<>();
		discriminatorMapping = new HashSet<>();
		externalDiscriminatorMapping = new HashSet<>();
		inheritedDiscriminatorMapping = new HashSet<>();

	}
	
	public Node(String resource) {
		this();
		this.resource=resource;		
		
		addDescription();

		this.inline = getInlineDefinition();
		
		Optional<JSONObject> optExpanded = getExpandedJSON();
		
		if(optExpanded.isPresent() && !optExpanded.get().isEmpty()) {
			this.inline = convertExpanded(optExpanded.get());
			
			LOG.debug("Node::getExpandedJSON resource={} optExpanded={}" , resource, optExpanded.get().toString(2) );
			LOG.debug("inline={}" , this.inline );

		} else {
			
			Property.Visibility visibility = Config.getBoolean(INCLUDE_INHERITED) ? Property.VISIBLE_INHERITED : Property.HIDDEN_INHERITED;

			addPropertyDetails(Property.BASE);									
			addAllOfs(visibility);
			addDiscriminatorMapping();	
		
		}
		
	}

	
	private String convertExpanded(JSONObject obj) {
		String res = "";
		if(obj.has(ITEMS)) {
			res = convertExpanded(obj.optJSONObject(ITEMS));
			
			String cardinality = APIModel.getCardinality(obj,false);
			if(!cardinality.isBlank()) {
				res = res + " [" + cardinality + "]";
			}
			
		} else if(obj.has(TYPE)) {
			res = obj.optString(TYPE);
		}
		return res;
	}

	private String getInlineDefinition() {
		JSONObject def = APIModel.getDefinition(this.resource);
		return getInlineDefinition(def);
	}
	

	private Optional<JSONObject> getExpandedJSON() {
		JSONObject def = APIModel.getDefinition(this.resource);
		return getExpandedJSON(def);
	}

	private Optional<JSONObject> getExpandedJSON(JSONObject obj) {
		Optional<JSONObject> res = Optional.empty();
		
		JSONObject clone = new JSONObject(obj.toString());

		if(obj!=null) {
			LOG.debug("Node::getFlatten resource={} def={}" , resource, obj.toString(2) );
			
			if(obj.has(ENUM) || obj.has(PROPERTIES) || obj.has(DISCRIMINATOR) ) return res;
			
			if(obj.has(REF)) {
				JSONObject refDef = APIModel.getDefinitionBySchemaObject(obj);
				res = getExpandedJSON(refDef);
				
			} else if(obj.has(ALLOF)) {
				
				JSONArray array = obj.optJSONArray(ALLOF);			
				res = getExpandedJSON(array);
				if(res.isPresent()) {
					partialOverwriteJSON(clone,res.get());
				}	
				clone.remove(ALLOF);
				res = Optional.of(clone);

			} else if(obj.has(ITEMS)) {
				
				JSONObject items = obj.optJSONObject(ITEMS);
				if(items!=null) {
					res = getExpandedJSON(items);
					if(res.isPresent()) {
						clone.put(ITEMS, res.get());
						res = Optional.of(clone);
					}
				} else {
					JSONArray array = obj.optJSONArray(ITEMS);
					res = getExpandedJSON(array);
					if(res.isPresent()) {
						clone.put(ITEMS, res.get());
						res = Optional.of(clone);
					}
					
				}
								
			} else {
				res = Optional.of(clone);
			}
		}
		
		if(res.isPresent()) LOG.debug("Node::getFlatten resource={} res={}" , resource, res );

		
		return res;
		
	}
	
	
	
	private Optional<JSONObject> getExpandedJSON(JSONArray array) {
		Optional<JSONObject> res = Optional.empty();
		
		LOG.debug("getExpandedJSON:: array={}",  array.toString(2));

		JSONObject clone = new JSONObject();
		
		Iterator<Object> iter = array.iterator();
		while(iter.hasNext()) {
			Object o = iter.next();
			LOG.debug("getExpandedJSON:: o={}",  o);
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = getExpandedJSON(obj);
				if(res.isPresent()) {
					partialOverwriteJSON(clone,res.get());
				} else {
					return res;
				}
			} else {
				LOG.debug("getExpandedJSON:: NOT PROCESSED o={}",  o);
			}
		}
		
		if(!clone.keySet().isEmpty()) {
			res = Optional.of(clone);
		} else {
			res = Optional.empty();
		}
		
		return res;
	}

	private void partialOverwriteJSON(JSONObject target, JSONObject source) {
		LOG.debug("partialOverwriteJSON:: source={}",  source.toString());
		for(String key : source.keySet()) {
			target.put(key, source.get(key));
		}
	}

	private String getInlineDefinition(JSONObject def) {
		String res = "";
		
		if(def!=null) {
			LOG.debug("Node::getInlineDefinition resource={} def={}" , resource, def.toString() );
			
			if(def.has(ENUM)) {
				res = "";
			} else if(def.has(PROPERTIES)) {
				res = "";
			} else if(def.has(REF)) {
				JSONObject refDef = APIModel.getDefinitionBySchemaObject(def);
				String cardinality = APIModel.getCardinality(refDef, false);

				res = getInlineDefinition(refDef) ;
				
				if(!cardinality.isBlank()) res = res + getCardinalityString(cardinality);
				
				LOG.debug("Node::getInlineDefinition resource={} refDef={}" , resource, refDef.toString() );

			} else if(def.has(ALLOF)) {
				JSONArray array = def.optJSONArray(ALLOF);
				
				res = getInlineDefinition(array);

				String cardinality = getCardinality(array, res);

				res = res + cardinality;
				

			} else if(def.has(ITEMS)) {
				
				String cardinality = APIModel.getCardinality(def, false);

				JSONObject obj = def.optJSONObject(ITEMS);
				if(obj!=null) {
					res = getInlineDefinition(obj);
				} else {
					JSONArray array = def.optJSONArray(ITEMS);
					Iterator<Object> iter = array.iterator();
					while(iter.hasNext()) {
						Object o = iter.next();
						if(o instanceof JSONObject) {
							obj = (JSONObject) o;
							res = getInlineDefinition(obj);
						} 
						if(!res.isBlank()) break;
					}
				}
				
				res = res + getCardinalityString(cardinality);	
				
			} else {
				String cardinality = APIModel.getCardinality(def, false);
				res = getType(def) + getCardinalityString(cardinality);	
				
				LOG.debug("Node::getInlineDefinition resource={} res={}" , resource, res );
			}
		}
		
		if(!res.isBlank()) LOG.debug("Node::getInlineDefinition resource={} res={}" , resource, res );

		return res;
		
	}

	private String getInlineDefinition(JSONArray array) {
		String res = "";
		for(Object o : array) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = getInlineDefinition(obj);
			} 
			if(!res.isBlank()) break;
		}
		return res;
	}

	private String getCardinality(JSONArray array, String definition) {
		String res = "";
		for(Object o : array) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = APIModel.getCardinality(obj, false);
			} 
			if(!res.isBlank()) break;
		}
		
		if(!definition.isBlank()) LOG.debug("getCardinality: definition={} res={} array={}",  definition, res, array);
		
		return res;
	}
	
	private String getCardinalityString(String cardinality) {
		String res = "";
		if(!cardinality.isBlank()) res = " [" + cardinality + "]";
		return res;
	}

	private String getType(JSONObject def) {
		String res="";
		
		if(def.has(ITEMS)) {
			if(def.optJSONArray(ITEMS)!=null) {
				JSONArray array = def.optJSONArray(ITEMS);
				res = getType(array);
			} else if(def.optJSONObject(ITEMS)!=null) {
				JSONObject obj = def.optJSONObject(ITEMS);
				res = getType(obj);
			} 
		}
		
		if(res.isBlank()) res = def.optString(TYPE);
		
		return res;
	}

	private String getType(JSONArray def) {
		String res="";
		
		for(Object o : def) {
			if(o instanceof JSONObject) {
				JSONObject obj = (JSONObject) o;
				res = getType(obj);
			} 
			
			if(!res.isBlank()) break;
		}
				
		return res;
	}
	

	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(Property.Visibility visibility) {
		JSONObject propObj = APIModel.getPropertyObjectForResource(this.resource);
		addPropertyDetails(propObj, visibility, null);
	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addPropertyDetails(JSONObject propObj, Property.Visibility visibility, JSONObject definition) {
			
		if(definition!=null) LOG.debug("addPropertyDetails: node={} definition={}" , this, definition.toString(2) );

		if(propObj.has(TYPE) && ARRAY.equals(propObj.optString(TYPE))) {
			Out.printAlways("addPropertyDetails: NOT PROCESSED propObj=" + propObj.toString(2) );
		} else  {
			
			List<String> required = new LinkedList<>();
			if(definition!=null) {
				required = Config.getListAsObject(definition, REQUIRED).stream().map(Object::toString).collect(toList());
			}
			
			for(String propName : propObj.keySet()) {
				JSONObject property = propObj.optJSONObject(propName);
				if(property!=null) {
					String type = APIModel.type(property);
		
					String coreType = APIModel.removePrefix(type);
					
					boolean isRequired = APIModel.isRequired(this.resource, propName) || required.contains(propName);
					String cardinality = APIModel.getCardinality(property, isRequired);
		
					boolean seen = properties.stream().map(Property::getName).anyMatch(propName::contentEquals);
					
					if(!seen) {
						Property propDetails = new Property(propName, coreType, cardinality, isRequired, property.optString(DESCRIPTION), visibility );
						
						LOG.debug("addPropertyDetails: node={} property={} " , this, propDetails );

						if(property.has(ENUM)) {
							
							List<Object> elements = Config.getListAsObject(property,ENUM);

							propDetails.addEnumValues( elements.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.toList()) );

							LOG.debug("addPropertyDetails: property={} values={}" , propName, propDetails.getValues() );

							boolean candidateNullable = elements.stream().anyMatch(Objects::isNull);
							
							if(property.optBoolean(NULLABLE) && candidateNullable) propDetails.setNullable();

						}
						
						properties.add( propDetails );
					} else {
						LOG.debug("addPropertyDetails: node={} property={} seen={}" , this, propName, seen );

					}
					
					if(APIModel.isEnumType(type) && !enums.contains(coreType)) {
						enums.add(coreType);
					}
				} else {
					Out.printAlways("... unexpected property in " + propObj.toString());
				}	
			}
		} 

	}

	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(Property.Visibility visibility) {
//		if(Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS)) {
//			JSONArray allOfs = APIModel.getAllOfForResource(this.resource);
//			addAllOfs(allOfs, visibility);
//		}
		
		JSONArray allOfs = APIModel.getAllOfForResource(this.resource);
		addAllOfs(allOfs, visibility);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addAllOfs(JSONArray allOfs, Property.Visibility visibility) {
		
		LOG.debug("addAllOfs: node={} addAllOfs={}", this, allOfs.toString(2));

		allOfs.forEach(allOf -> {
			if(allOf instanceof JSONObject) {
				JSONObject definition = (JSONObject) allOf;
				addAllOfObject(definition, visibility);
			}
		});
				
	}

	@LogMethod(level=LogLevel.DEBUG)
	public void addAllOfObject(JSONObject definition, Property.Visibility visibility) {
		
		if(Config.getBoolean(EXPAND_ALL_PROPERTIES_FROM_ALLOFS)) {
			if(definition.has(REF)) {
				String type = APIModel.getTypeByReference(definition.optString(REF));
					
				if(Config.getBoolean(EXPAND_INHERITED)) {
					this.addInheritance(type);
				}
					
				if(Config.getBoolean(INCLUDE_INHERITED)) {
					JSONObject obj = APIModel.getDefinitionBySchemaObject(definition);
					addAllOfObject(obj,Property.VISIBLE_INHERITED);
				}	
			}
		}
		
		if(definition.has(PROPERTIES)) {
			JSONObject obj = APIModel.getPropertyObjectBySchemaObject(definition);			
			if(obj!=null) {	
				addPropertyDetails(obj,visibility,definition);				
			}
		}
		
		if(definition.has(ALLOF)) {
			addAllOfs(definition.optJSONArray(ALLOF), visibility);
		}
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void addDiscriminatorMapping() {		
		JSONObject mapping = APIModel.getMappingForResource(this.resource);
		
		if(mapping!=null && !mapping.isEmpty()) {
			Set<String> mappings = new HashSet<>(mapping.keySet());
			
			this.discriminatorMapping.addAll(mappings);
			// this.discriminatorMapping.remove(this.resource);

			mappings = new HashSet<>(mapping.keySet());
			this.externalDiscriminatorMapping.addAll(mappings);
			this.externalDiscriminatorMapping.remove(this.resource);
			
			LOG.debug("addDiscriminatorMapping: node={} mapping={}",  this.getName(), this.discriminatorMapping);
		}
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public void resetPlacement() {
		this.placements = new HashMap<>();
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	private void addDescription() {   	
	    this.description = APIModel.getDescription(this.resource);
	}

	
	public List<Property> getProperties() {
		return this.properties;
	}

	public List<OtherProperty> getOtherProperties() {
		return this.otherProperties;
	}

	public String getDescription() {
		return this.description;
	}
	
	public String toString() {
		return this.resource;
	}
	
	public int hashCode() {
		int res = this.resource.hashCode();
		LOG.trace("Node::hashCode: node=" + this.toString() + " res=" + res);

		return res;
	}
	
	public boolean equals(Object obj) {
		boolean res=false;
		if(obj instanceof Node) {
			res = ((Node) obj).getName().contentEquals(this.getName());
		} 
		LOG.trace("Node::equals: node=" + this.toString() + " obj=" + obj + " res=" + res);
		return res;
	}
	
	public String getName() {
		return this.resource;
	}

	public List<String> getEnums() {
		return this.enums;
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType() {
		return isSimpleType(this.getName()) && !isEnumType(this.getName());
	}

	@LogMethod(level=LogLevel.DEBUG)
	public boolean isEnumType(String type) {
		return APIModel.isEnumType(type);
	}


	@LogMethod(level=LogLevel.DEBUG)
	public boolean isSimpleType(String type) {
		
		List<String> simpleEndings = Config.getSimpleEndings();
				
		boolean simpleEnding = simpleEndings.stream().anyMatch(type::endsWith);
		
		return  simpleEnding 
				|| APIModel.isSpecialSimpleType(type) 
				|| APIModel.isSimpleType(type) 
				|| Config.getSimpleTypes().contains(type) 
				|| APIModel.isEnumType(type);
		
	}
	
	@LogMethod(level=LogLevel.DEBUG)
	public Set<Property> getReferencedProperties() {
		return properties.stream()
				.filter(this::isReferenceType)
				.collect(toSet());
	}
		
	@LogMethod(level=LogLevel.DEBUG)
	public boolean isReferenceType(Property property) {
		return !isSimpleType(property.getType());
	}
	
	public boolean startsWith(Node node) {
		return this.getName().startsWith(node.getName());
	}

	@Override
	public int compareTo(Object o) {
		if(o instanceof Node) {
			Node n = (Node)o;
			return this.getName().compareTo(n.getName());
		} else {
			return -1;
		}	
	}
	
	public String getDetails() {
		StringBuilder res = new StringBuilder();
		
		properties.stream().forEach( prop -> res.append(prop + " ")) ;
		
		return res.toString();
	}
	
	public boolean isEnumNode() {
		return this instanceof EnumNode;
	}

	public boolean inheritsFrom(Graph<Node, Edge> graph, Node candidate) {
		// return graph.getAllEdges(this, candidate).stream().anyMatch(edge -> edge instanceof AllOf);
		return graph.getAllEdges(this, candidate).stream().anyMatch(Edge::isInheritance);

	}

	public void addInheritance(String type) {
		this.inheritance.add(type);
	}

	public Set<String> getInheritance() {
		return this.inheritance;
	}
	
	
	public Set<String> getDiscriminatorMapping() {
		Set<String> all = this.discriminatorMapping;
		
		return all;
	}

	public Set<String> getInheritedDiscriminatorMapping() {		
		Set<String> inherited = this.inheritedDiscriminatorMapping;
				
		return inherited;
	}

	
	public Set<String> getAllDiscriminatorMapping() {		
		Set<String> all = new HashSet<>();
		
		all.addAll(this.discriminatorMapping);
		all.addAll(this.inheritedDiscriminatorMapping);
				
		return all;
	}
	
	public Set<String> getExternalDiscriminatorMapping() {
		return this.externalDiscriminatorMapping;
	}

	Set<Node> circleNodes = new HashSet<>();
	public void addCircleElements(Collection<Node> circle) {
		circleNodes.addAll(circle);
	}
	
	public boolean isPartOfCircle() {
		return !circleNodes.isEmpty();
	}
	
	public Set<Node> getCircleNodes() {
		return this.circleNodes;
	}

	public String getInline() {
		return inline;
	}

	public void setInheritedDiscriminatorMapping(Set<String> inheritedDiscriminators) {
		this.inheritedDiscriminatorMapping=inheritedDiscriminators;
	}
	
}


