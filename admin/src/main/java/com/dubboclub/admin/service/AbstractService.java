package com.dubboclub.admin.service;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.dubboclub.admin.model.BasicModel;
import com.dubboclub.admin.sync.RegistryServerSync;
import com.dubboclub.admin.sync.util.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by bieber on 2015/6/3.
 */
public abstract class AbstractService {

    private RegistryServerSync registryServerSync;

    protected void update(URL oldURL,URL newURL){
        registryServerSync.update(oldURL,newURL);
    }

    protected void delete(URL url){
        registryServerSync.unregister(url);
    }

    protected void add(URL url){
        registryServerSync.register(url);
    }


    public void setRegistryServerSync(RegistryServerSync registryServerSync) {
        this.registryServerSync = registryServerSync;
    }

    protected ConcurrentMap<String, Map<Long, URL>> getServiceByCategory(String category){
        return registryServerSync.getRegistryCache().get(category);
    }

    protected URL getOneById(String category,long id){
        ConcurrentMap<String, Map<Long, URL>>  categoryMap = registryServerSync.getRegistryCache().get(category);
        for(Map.Entry<String, Map<Long, URL>> entry:categoryMap.entrySet()){
            if(entry.getValue().containsKey(id)){
                return entry.getValue().get(id);
            }
        }
        throw new IllegalStateException("data had changed!");
    }

    //通过对某个目录下的数据定义过滤器，过滤出复核条件的数据
    protected<T extends BasicModel> List<T>  filterCategoryData(ConvertURL2Entity<T> convertURLTOEntity,String category,String... params){
        if(params.length%2!=0){
            throw  new IllegalArgumentException("filter params size must be paired");
        }
        Map<String,String> filter = new HashMap<String,String>();
        for(int i=0;i<params.length;i=i+2){
            filter.put(params[i],params[i+1]);
        }
        Collection<Map.Entry<Long,URL>> urls = filterCategoryData(filter,category);
        List<T> entities = new ArrayList<T>();
        for(Map.Entry<Long,URL> url:urls){
            T item =convertURLTOEntity.convert(new Pair<Long, URL>(url));
            if(item==null){
                continue;
            }
            entities.add(item);
        }
        return entities;
    }

    protected Collection<Map.Entry<Long,URL>> filterCategoryData(Map<String,String> filter,String category){
        ConcurrentMap<String, Map<Long, URL>> services = getServiceByCategory(category);
        List<Map.Entry<Long,URL>> matchedData = new ArrayList<Map.Entry<Long,URL>>();
        //该目录下面所有的服务信息
        if(services!=null){
            //某个服务下面所有的配置信息
            //默认情况一个服务只属于一个提供者应用
            Collection<Map<Long, URL>> servicesUrls = services.values();
            for(Map<Long, URL> urls:servicesUrls){
                for(Map.Entry<Long,URL> entry:urls.entrySet()){
                    Map<String,String> parameters = entry.getValue().getParameters();
                    if(parameters!=null){
                        boolean matched=true;
                        for(Map.Entry<String,String> filterEntry:filter.entrySet()){
                            String filterValue = filterEntry.getValue();
                            String paramValue = parameters.get(filterEntry.getKey());
                            if(Constants.ANY_VALUE.equals(paramValue)){
                                parameters=null;
                            }

                            if(StringUtils.isEmpty(paramValue)&&StringUtils.isEmpty(filterValue)){
                                continue;
                            }else if(!StringUtils.isEmpty(paramValue)&&!paramValue.equals(filterValue)){
                                matched=false;
                                break;
                            }else if(!StringUtils.isEmpty(filterValue)&&!filterValue.equals(paramValue)) {
                                matched=false;
                                break;
                            }
                        }
                        if(matched){
                            matchedData.add(entry);
                        }
                    }
                }
            }
        }
        return matchedData;
    }


    public interface ConvertURL2Entity<T extends BasicModel>{

        public T convert(Pair<Long, URL> pair);
    }


}
