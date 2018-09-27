package com.apposcopy.app;

import java.util.*;
import java.util.zip.*;
import java.io.*;

import com.apposcopy.ella.Config;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;

import com.google.common.io.Files;

import com.apposcopy.ella.Util;//remove this dependency

/**
 * @author Saswat Anand
 **/
public class App
{
	private List<Component> comps = new ArrayList<>();
	private Set<String> permissions = new HashSet<>();

	private String pkgName;
	private String version;
	private String iconPath;

	private HashMap<String, DexFile> dexFile;
	private HashMap<String, String> dexFilePath;
	private String inputFile;

	private File manifestFile;
	private String apktoolOutDir;

	public App(String inputFile) throws IOException
	{
		this.inputFile = inputFile;
		if(inputFile.endsWith(".apk")){			
			this.dexFilePath = extractClassesDex(inputFile);
		} else {
			this.dexFilePath = new HashMap<>();
			this.dexFilePath.put("classes.dex", inputFile);
		}
		this.dexFile = new HashMap<>();
		for (HashMap.Entry<String, String> de : dexFilePath.entrySet())
			this.dexFile.put(de.getValue(), DexFileFactory.loadDexFile(de.getValue(), 15));
	}

	public static App readApp(String inputFile, String scratchDir, String apktoolJar) throws IOException
	{
		App app = new App(inputFile);
		if(inputFile.endsWith(".apk")){			
			app.apktoolOutDir = app.runApktool(scratchDir, apktoolJar);
			ParseManifest pmf = new ParseManifest(new File(app.apktoolOutDir, "AndroidManifest-deres.xml"), app);
			app.process(app.apktoolOutDir);
			app.manifestFile = pmf.manifestFile();

		}
		return app;
	}

	static HashMap<String, String> extractClassesDex(String inputFile) throws IOException
	{
		HashMap<String, String> dexMap = new HashMap<>();
		BufferedInputStream bin = new BufferedInputStream(new FileInputStream(inputFile));
		ZipInputStream zin = new ZipInputStream(bin);
		ZipEntry ze;
		while ((ze = zin.getNextEntry()) != null) {
			if (ze.getName().endsWith(".dex")) {
				File inputDexFile = File.createTempFile("inputclasses",".dex");
				dexMap.put(ze.getName(), inputDexFile.getAbsolutePath());
				OutputStream out = new FileOutputStream(inputDexFile);
				byte[] buffer = new byte[8192];
				int len;
				while ((len = zin.read(buffer)) != -1) {
					out.write(buffer, 0, len);
				}
				out.close();
			}
		} 
		return dexMap;
	}

	public static void signAndAlignApk(File unsignedApk, String signedApkPath, String keyStore, String storePass, String keyPass, String alias, String zipAlignPath)
	{
		String[] argsSign = {"jarsigner", 
							 "-keystore", keyStore,
							 "-storepass", storePass,
							 "-keypass", keyPass,
							 unsignedApk.getAbsolutePath(),
							 alias};						 
		try{
			for(String s : argsSign) System.out.print(" "+s); System.out.println("");
			int exitCode = Runtime.getRuntime().exec(argsSign).waitFor();
			if(exitCode != 0)
				throw new Error("Error in running jarsigner "+exitCode);
		}catch(Exception e){
			throw new Error("Error in running jarsigner", e);
		}
		
		String[] argsAlign = {zipAlignPath, 
							  "-f", "4",
							  unsignedApk.getAbsolutePath(),
							  signedApkPath};
		try{
			for(String s : argsAlign) System.out.print(" "+s); System.out.println("");
			int exitCode = Runtime.getRuntime().exec(argsAlign).waitFor();
			if(exitCode != 0)
				throw new Error("Error in running zipalign");
		}catch(Exception e){
			throw new Error("Error in running zipalign", e);
		}
	}

	private String runApktool(String scratchDir, String apktoolJar)
	{
		// w/ quick hack
		String apktoolOutDir = scratchDir+File.separator+"apktool-out";
		// String apktoolOutDir_deres = scratchDir+File.separator+"apktool-out-deres";
		String[] args = {"java", "-Xmx1g", "-ea",
						 "-classpath", apktoolJar,
						 "brut.apktool.Main",
						 "d", "-r", "-f", "--frame-path", scratchDir,
						 "-o", apktoolOutDir,
						 "-s", inputFile};
		String[] args_apkanalyzer = {
				"bash", Config.g().extras.get("ella.x.aapath") + File.separator + "apkanalyzer",
				"manifest", "print", inputFile
		};
		// String[] args_deres = {"java", "-Xmx1g", "-ea",
		// 				 "-classpath", apktoolJar,
		// 				 "brut.apktool.Main",
		// 				 "d", "-f", "--frame-path", scratchDir,
		// 				 "-o", apktoolOutDir_deres,
		// 				 "-s", inputFile};

		try{
			int exitCode;

			// exitCode = Runtime.getRuntime().exec(args_deres).waitFor();
			// if(exitCode != 0) {
			// 	System.err.println("java -Xmx1g -ea -classpath " + apktoolJar + " brut.apktool.Main d -f --frame-path " + scratchDir + " -o " + apktoolOutDir_deres + " -s " + inputFile);
			// 	throw new Error("Error in running apktool");
			// }

			exitCode = Runtime.getRuntime().exec(args).waitFor();
			if(exitCode != 0) {
				System.err.println("java -Xmx1g -ea -classpath " + apktoolJar + " brut.apktool.Main d -r -f --frame-path " + scratchDir + " -o " + apktoolOutDir + " -s " + inputFile);
				throw new Error("Error in running apktool");
			}

			// Files.copy(
			// 	new File(apktoolOutDir + File.separator + "AndroidManifest.xml"),
			// 	new File(apktoolOutDir + File.separator + "AndroidManifest.xml.bak")
			// );

			Process p = Runtime.getRuntime().exec(args_apkanalyzer);

			String line;
			BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
			BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			PrintWriter printWriter = new PrintWriter(new FileWriter(
					apktoolOutDir + File.separator + "AndroidManifest-deres.xml"));
			while ((line = bri.readLine()) != null) {
				// System.out.println(line);
				printWriter.println(line);
			}
			bri.close();
			while ((line = bre.readLine()) != null) {
				// System.out.println(line);
				printWriter.println(line);
			}
			bre.close();
			printWriter.close();
			p.waitFor();

			// Files.copy(
			// 	new File(apktoolOutDir_deres + File.separator + "AndroidManifest.xml"),
			// 	new File(apktoolOutDir + File.separator + "AndroidManifest-deres.xml")
			// );
		}catch(Exception e){
			e.printStackTrace();
			// throw new Error("Error in running apktool");
		}
		return apktoolOutDir;
	}

	public void listClasses()
	{
		for (HashMap.Entry<String, String> de : dexFilePath.entrySet()) {
			System.out.println(de.getKey() + " =>");
			DexFile dexFile = this.dexFile.get(de.getValue());
			for (ClassDef classDef : dexFile.getClasses()) {
				String className = Util.dottedClassName(classDef.getType());
				System.out.println(className);
			}
		}
	}

	public void process(String apktoolOutDir)
	{
		if(iconPath != null){
			if(iconPath.startsWith("@drawable/")){
				String icon = iconPath.substring("@drawable/".length()).concat(".png");
				File f = new File(apktoolOutDir.concat("/res/drawable"), icon);
				if(f.exists())
					iconPath = f.getPath();
				else {
					f = new File(apktoolOutDir.concat("/res/drawable-hdpi"), icon);
					if(f.exists())
						iconPath = f.getPath();
					else
						iconPath = null;
				}
			} else
				iconPath = null;
		}

		List<Component> comps = this.comps;
		this.comps = new ArrayList<>();

		Set<String> compNames = new HashSet<>();
		for(Component c : comps){
			//System.out.println("@@ "+c.name);
			compNames.add(c.name);
		}

		filterDead(compNames);

		//System.out.println("^^ "+compNames.size());
		
		for(Component c : comps){
			if(compNames.contains(c.name))
				this.comps.add(c);
		}

	}

	public HashMap<String, String> dexFilePath()
	{
		return dexFilePath;
	}

	public void updateApk(HashMap<String, String> newDexFilePath, File newManifestFile, File outputFile, String apktoolJar) throws IOException
	{
		if(!inputFile.endsWith(".apk")){
			assert inputFile.endsWith(".dex") && outputFile.getName().endsWith(".dex");
			Files.copy(new File(newDexFilePath.get("classes.dex")), outputFile);
			return;
		}
		
		assert outputFile.getName().endsWith(".apk");

		Files.copy(newManifestFile, new File(apktoolOutDir, "AndroidManifest-new.xml"));
		// Files.copy(new File(newDexFilePath), new File(apktoolOutDir, "classes2.dex"));
		for (HashMap.Entry<String, String> newDexFile : newDexFilePath.entrySet())
			Files.copy(new File(newDexFile.getValue()), new File(apktoolOutDir, newDexFile.getKey()));

		String[] args = {"java", "-Xmx1g", "-ea",
						 "-classpath", apktoolJar,
						 "brut.apktool.Main",
						 "b",  "-o", outputFile.getAbsolutePath(), apktoolOutDir};
						 
		try{
			
			Process p = Runtime.getRuntime().exec(args);
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
			while(reader.readLine() != null);
			int exitCode = p.waitFor();
			if(exitCode != 0) {
				for(String a : args) System.out.print(a+" ");
				System.out.println("");
				throw new Error("Error in repackaging the apk."); 
			}
		}catch(Exception e){
			for(String a : args) System.out.print(a+" ");
			System.out.println("");
			throw new Error("Error in repackaging the apk.");
		}

		/*
		//stick the new dex file into the output apk
		ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(inputFile)));
		ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outputFile));
		ZipEntry ze = null;
		byte[] buffer = new byte[8192];
		int len;
		while ((ze = zin.getNextEntry()) != null) {
			String entryName = ze.getName();
			if(entryName.startsWith("META-INF"))
				continue;
			zout.putNextEntry(new ZipEntry(entryName));
			if(entryName.equals("AndroidManifest.xml")) {
				//write the instrumented classes.dex
				BufferedInputStream bin = new BufferedInputStream(new FileInputStream(newManifestFile));
				while ((len = bin.read(buffer)) != -1) {
					zout.write(buffer, 0, len);
				}
				bin.close();
			} else if(entryName.equals("classes.dex")) {
				//write the instrumented classes.dex
				BufferedInputStream bin = new BufferedInputStream(new FileInputStream(newDexFilePath));
				while ((len = bin.read(buffer)) != -1) {
					zout.write(buffer, 0, len);
				}
				bin.close();
			} else {
				//just copy it
				while ((len = zin.read(buffer)) != -1) {
					zout.write(buffer, 0, len);
				}
			}
			zout.closeEntry();
		}
		zin.close();
		zout.close();
		*/
	}

	public File manifestFile()
	{
		return this.manifestFile;
	}

	public List<Component> components()
	{
		return comps;
	}

	public Set<String> permissions()
	{
		return permissions;
	}

	public void setPackageName(String pkgName)
	{
		this.pkgName = pkgName;
	}

	public String getPackageName()
	{
		return this.pkgName;
	}
	
	public void setVersion(String version)
	{
		this.version = version;
	}

	public String getVersion()
	{
		return this.version;
	}

	public void setIconPath(String icon)
	{
		this.iconPath = icon;
	}
	
	public String getIconPath()
	{
		return iconPath;
	}

	private void filterDead(Set<String> compNames)
	{
		Set<String> compNamesAvailable = new HashSet<>();
		for (HashMap.Entry<String, String> de : dexFilePath.entrySet()) {
			DexFile dexFile = this.dexFile.get(de.getValue());
			for (ClassDef classDef : dexFile.getClasses()) {
				String className = classDef.getType();
				if (className.charAt(0) == 'L') {
					int len = className.length();
					assert className.charAt(len - 1) == ';';
					className = className.substring(1, len - 1);
				}
				className = className.replace('/', '.');
				String tmp = className;
				//String tmp = className.replace('$', '.');
				if (compNames.contains(tmp)) {
					compNamesAvailable.add(tmp);
					//System.out.println("%% "+tmp);
				}
			}
		}

		compNames.clear();
		compNames.addAll(compNamesAvailable);
	}
	
	public String toString()
	{
		StringBuilder builder = new StringBuilder("App : {\n");

		builder.append("package: ").append(pkgName).append("\n");
		builder.append("version: ").append(version).append("\n");

		builder.append("comps : {\n");
		for(Component c : comps){
			builder.append("\t").append(c.toString()).append("\n");
		}
		builder.append("}\n");

		builder.append("perms: {\n");
		for(String perm : permissions){
			builder.append("\t").append(perm).append("\n");
		}
		builder.append("}\n");

		builder.append("}");
		return builder.toString();
	}
}
