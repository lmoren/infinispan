/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.tools.schema;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.infinispan.Version;
import org.infinispan.config.AbstractConfigurationBean;
import org.infinispan.config.ConfigurationElement;
import org.infinispan.config.ConfigurationElements;
import org.infinispan.util.ClassFinder;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SchemaGenerator {
   private final List<Class<?>> beans;
   private final TreeNode root;
   private final File fileToWrite;
   
   protected SchemaGenerator(File fileToWrite, String searchPath) throws Exception {
      this.fileToWrite = fileToWrite;
      List<Class<?>> infinispanClasses = null;
      String pathUsed = ClassFinder.PATH;
      if (searchPath == null || searchPath.length() == 0) {
         infinispanClasses = ClassFinder.infinispanClasses();
      } else {
         pathUsed = searchPath;
         infinispanClasses = ClassFinder.infinispanClasses(searchPath);
      }
      if (infinispanClasses == null || infinispanClasses.isEmpty())
         throw new IllegalArgumentException("Could not find infinispan classes on your classpath "
                  + pathUsed);

      beans = ClassFinder.isAssignableFrom(infinispanClasses, AbstractConfigurationBean.class);
      if (beans.isEmpty())
         throw new IllegalStateException("Could not find AbstractConfigurationBean(s) on your classpath " 
                  + pathUsed);
      
      root = tree(beans);
   }
   
   private TreeNode findNode(TreeNode tn, String name, String parent){
      TreeNode result = null;
      if(tn.getName().equals(name) && tn.getParent() != null && tn.getParent().getName().equals(parent)){         
         result = tn;
      } else {
         for (TreeNode child :tn.getChildren()){
            result = findNode(child,name,parent);
            if(result != null) break;
         }
      }
      return result;
   }
   
   private TreeNode tree(List<Class<?>>configBeans){
      List<ConfigurationElement> lce = new ArrayList<ConfigurationElement>(7);
      for (Class<?> clazz : configBeans) {
         ConfigurationElement ces[] = null;
         ConfigurationElements configurationElements = clazz.getAnnotation(ConfigurationElements.class);
         ConfigurationElement configurationElement = clazz.getAnnotation(ConfigurationElement.class);

         if (configurationElement != null && configurationElements == null) {
            ces = new ConfigurationElement[]{configurationElement};
         }
         if (configurationElements != null && configurationElement == null) {
            ces = configurationElements.elements();
         }
         if(ces != null){
            lce.addAll(Arrays.asList(ces));
         }
      }
      TreeNode root = new TreeNode("infinispan",new TreeNode());      
      makeTree(lce,root);
      return root;
   }
   
   private void makeTree(List<ConfigurationElement> lce, TreeNode tn) {
      for (ConfigurationElement ce : lce) {
         if(ce.parent().equals(tn.getName())){
            TreeNode child = new TreeNode(ce.name(),tn);
            tn.getChildren().add(child);
            makeTree(lce,child);
         }
      }
   }
   
   public static void main(String[] args) throws Exception {
      String outputDir = "./";

      for (int i = 0; i < args.length; i++) {
         String arg = args[i];
         if ("-o".equals(arg)) {
            outputDir = args[++i];
            continue;
         } else {
            System.out.println("SchemaGenerator -o <path to newly created xsd schema file>");
            return;
         }
      }

      File f = new File(outputDir, "infinispan-config-" +Version.getMajorVersion() + ".xsd");
      SchemaGenerator sg = new SchemaGenerator(f,null);
      sg.generateSchema();
   }

   private void generateSchema() {
      try {
         FileWriter fw = new FileWriter(fileToWrite, false);
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         DocumentBuilder builder = factory.newDocumentBuilder();
         DOMImplementation impl = builder.getDOMImplementation();
         Document xmldoc = impl.createDocument("http://www.w3.org/2001/XMLSchema", "xs:schema",null);
         xmldoc.getDocumentElement().setAttribute("targetNamespace", "urn:infinispan:config:" + Version.getMajorVersion());
         xmldoc.getDocumentElement().setAttribute("xmlns:tns","urn:infinispan:config:" + Version.getMajorVersion());
         xmldoc.getDocumentElement().setAttribute("elementFormDefault", "qualified");
        
         Element xsElement = xmldoc.createElement("xs:element");       
         xsElement.setAttribute("name", "infinispan");
         xsElement.setAttribute("type", "tns:infinispanType");
         xmldoc.getDocumentElement().appendChild(xsElement);

         SchemaGeneratorTreeWalker tw = new SchemaGeneratorTreeWalker(xmldoc,beans);
         root.acceptPreOrderTreeWalker(tw);

         DOMSource domSource = new DOMSource(xmldoc);
         StreamResult streamResult = new StreamResult(fw);
         TransformerFactory tf = TransformerFactory.newInstance();
         Transformer serializer = tf.newTransformer();
         serializer.setOutputProperty(OutputKeys.METHOD, "xml");
         serializer.setOutputProperty(OutputKeys.INDENT, "yes");
         serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
         serializer.transform(domSource, streamResult);                 
         fw.flush();
         fw.close();
      } catch (Exception e) {
         e.printStackTrace();
      }      
   }
}
