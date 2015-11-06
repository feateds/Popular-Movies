package pt.ruiadrmartins.popularmovies;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class PopularMoviesFragment extends Fragment {

    private final String MOVIE_LIST_PARCELABLE_KEY = "movieList";
    private MoviesAdapter adapter;
    private ArrayList<Movie> movieList;
    private GridView gridView;
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;

    public PopularMoviesFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get saved data if it was stored in savedInstanceState
        // or initialize movie list array
        if(savedInstanceState == null || !savedInstanceState.containsKey(MOVIE_LIST_PARCELABLE_KEY)) {
            movieList = new ArrayList<>();
            updateMovieList();
        } else {
            movieList = savedInstanceState.getParcelableArrayList(MOVIE_LIST_PARCELABLE_KEY);
        }
        // Got idea from http://stackoverflow.com/a/8668012
        // to only register listener when onPause
        // and unregister on onResume
        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener(){
            @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
                if (key.equals(getString(R.string.pref_sort_key))) {
                    updateMovieList();
                }
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        gridView = (GridView) rootView.findViewById(R.id.gridview);
        adapter = new MoviesAdapter(getActivity(), movieList);
        gridView.setAdapter(adapter);

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        // register listener when settings activity is launched
        prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        // unregister listener when settings activity is closed
        prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // save list data in saveInstanceState to access after rotation
        outState.putParcelableArrayList(MOVIE_LIST_PARCELABLE_KEY, movieList);
        super.onSaveInstanceState(outState);
    }

    public void updateMovieList() {
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String sortBy = prefs.getString(getString(R.string.pref_sort_key), getString(R.string.pref_sort_default));
        FetchMoviesTask fetchMoviesTask = new FetchMoviesTask();
        fetchMoviesTask.execute(sortBy);
    }

    public class FetchMoviesTask extends AsyncTask<String,Void,ArrayList<Movie>> {

        private final String LOG_TAG = FetchMoviesTask.class.getSimpleName();

        @Override
        protected ArrayList<Movie> doInBackground(String... params) {

            final String FORECAST_BASE_URL = "http://api.themoviedb.org/3/discover/movie?";
            final String SORT_PARAM = "sort_by";
            final String API_PARAM = "api_key";

            String moviesJson;

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String order = params[0];

            try {
                Uri uri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(SORT_PARAM, order)
                        .appendQueryParameter(API_PARAM, BuildConfig.TMDB_API_KEY)
                        .build();

                URL url = new URL(uri.toString());

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }

                moviesJson = buffer.toString();

                Log.v(":D",moviesJson);

                return getMovieDataFromJson(moviesJson);

            }  catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } catch (JSONException e) {
                Log.e(LOG_TAG, "JSON Error ", e);
                return null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

        }

        private ArrayList<Movie> getMovieDataFromJson(String movieJson) throws JSONException {

            final String POSTER_QUAL_HIGH = "w500";
            final String POSTER_QUAL_LOW = "w342";

            final String TITLE_ELEMENT = "title";
            final String POSTER_PATH_ELEMENT = "poster_path";
            final String SYNOPSIS_ELEMENT = "overview";
            final String RATING_ELEMENT = "vote_average";
            final String RELEASE_DATE_ELEMENT = "release_date";
            final String BASE_POSTER_URL = "http://image.tmdb.org/t/p/" + POSTER_QUAL_LOW + "/";

            JSONObject mJson = new JSONObject(movieJson);
            JSONArray resultsArray = mJson.getJSONArray("results");

            ArrayList<Movie> result = new ArrayList<>();

            for(int i=0;i<resultsArray.length();i++) {
                // original title

                JSONObject movieData = resultsArray.getJSONObject(i);

                String title = movieData.getString(TITLE_ELEMENT);
                String poster = BASE_POSTER_URL + movieData.getString(POSTER_PATH_ELEMENT);
                String synopsis = movieData.getString(SYNOPSIS_ELEMENT);
                double rating = movieData.getDouble(RATING_ELEMENT);
                String releaseDate = movieData.getString(RELEASE_DATE_ELEMENT);

                Movie movie = new Movie(title,poster,synopsis,rating,releaseDate);

                result.add(movie);
            }

            return result;
        }

        @Override
        protected void onPostExecute(ArrayList<Movie> movies) {
            super.onPostExecute(movies);
            adapter.clear();
            if(movies!=null) {
                movieList = movies;
            }
            adapter = new MoviesAdapter(getActivity(), movieList);
            gridView.setAdapter(adapter);
        }
    }
}
