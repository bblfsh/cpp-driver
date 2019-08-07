template<class Class,typename Type,Type (Class::*PtrToMemberFunction)()const>
struct const_mem_fun
{
  typedef typename remove_reference<Type>::type result_type;

  template<typename ChainedPtr>

  typename disable_if<is_convertible<const ChainedPtr&,const Class&>,Type>::type

  operator()(const ChainedPtr& x)const
  {
    return operator()(*x);
  }

  Type operator()(const Class& x)const
  {
    return (x.*PtrToMemberFunction)();
  }

  Type operator()(const reference_wrapper<const Class>& x)const
  {
    return operator()(x.get());
  }

  Type operator()(const reference_wrapper<Class>& x)const
  {
    return operator()(x.get());
  }
};
