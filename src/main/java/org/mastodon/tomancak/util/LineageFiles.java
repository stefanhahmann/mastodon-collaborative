package org.mastodon.tomancak.util;

import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.project.MamutProject;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.ModelImporter;

import java.io.IOException;
import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.stream.Stream;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import java.util.Date;
import java.text.SimpleDateFormat;

public class LineageFiles
{
	// --------------- files names handling methods ---------------
	static public
	final SimpleDateFormat dateFormatter
		= new SimpleDateFormat("yyyy-MM-dd__HH-mm-ss__");

	static public
	final Predicate<String> lineageFilePattern
		= Pattern.compile("[2-9][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9]__[012][0-9]-[0-5][0-9]-[0-5][0-9]__.*\\.mstdn").asPredicate();


	static public
	Path getProjectRootFoldername(final MastodonPluginAppModel pluginAppModel)
	{
		return getProjectRootFoldername(pluginAppModel.getWindowManager().getProjectManager().getProject());
	}

	static public
	Path getProjectRootFoldername(final MamutProject mp)
	{
		final File pRoot = mp.getProjectRoot();
		return pRoot.isDirectory()? pRoot.toPath() : pRoot.getParentFile().toPath();
	}


	static public
	String lineageFilename(final String userName)
	{
		return (dateFormatter.format(new Date()) + userName + ".mstdn");
	}

	static public
	Stream<Path> listLineageFiles(final Path parentFolder) throws IOException
	{
		return Files
			.walk(parentFolder,1,FileVisitOption.FOLLOW_LINKS)
			.filter( p -> lineageFilePattern.test(p.getFileName().toString()) );
	}

	static public
	String dateOfLineageFile(final String filename)
	{
		return filename.substring(0,20);
	}

	static public
	String authorOfLineageFile(final String filename)
	{
		return filename.substring(22, filename.length()-6);
	}


	// --------------- loading/saving methods ---------------
	static public
	void startImportingModel(final Model model)
	{
		new ModelImporter( model ){{ startImport(); }};
	}

	static public
	void finishImportingModel(final Model model)
	{
		new ModelImporter( model ){{ finishImport(); }};
	}

	static public
	Model createEmptyModelWithUnitsOf(final Model refModel)
	{
		return new Model(refModel.getSpaceUnits(),refModel.getTimeUnits());
	}


	static public
	void loadLineageFileIntoModel(final Path filename, final Model model)
	throws IOException
	{
		final ZippedModelReader reader = new ZippedModelReader(filename);
		model.loadRaw( reader );
		reader.close();
	}

	static public
	void saveModelIntoLineageFile(final Model model, final Path filename)
	throws IOException
	{
		final ZippedModelWriter writer = new ZippedModelWriter(filename);
		model.saveRaw( writer );
		writer.close();
	}
}
