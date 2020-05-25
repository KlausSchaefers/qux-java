package de.vommond.lunarmare;

import de.vommond.lunarmare.impl.ArrayField;
import de.vommond.lunarmare.impl.BooleanField;
import de.vommond.lunarmare.impl.DateField;
import de.vommond.lunarmare.impl.DoubleField;
import de.vommond.lunarmare.impl.FloatField;
import de.vommond.lunarmare.impl.IntArrayField;
import de.vommond.lunarmare.impl.IntegerField;
import de.vommond.lunarmare.impl.LongField;
import de.vommond.lunarmare.impl.ObjectField;
import de.vommond.lunarmare.impl.StringField;

public interface ModelBuilder {

	public Model build();
	
	public abstract IntegerField addInteger(String name);

	public abstract DoubleField addDouble(String name);

	public abstract StringField addString(String name);

	public abstract LongField addLong(String name);

	public abstract DateField addDate(String name);

	public abstract ObjectField addObject(String name);

	public abstract ArrayField addArray(String name);

	public abstract IntArrayField addIntArray(String name);

	public abstract FloatField addFloat(String name);

	public abstract BooleanField addBoolean(String name);
}
